#!/usr/bin/env python3
"""Build test-only plugin, then launch isolated RustRover headless processes for plugin combinations."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument('--ide', required=True, type=Path)
parser.add_argument('--reuse-config', action='store_true', help='Keep task-owned sandbox configuration after activating a valid IDE license there')
parser.add_argument('--mode', choices=['core-only', 'core-riscv', 'core-ld', 'all', 'extension'], help='Run only one plugin combination')
args = parser.parse_args()
ide = args.ide.resolve()
root = Path(__file__).resolve().parents[2]
work = root / 'build' / 'smoke'
work.mkdir(parents=True, exist_ok=True)
version = next(line.split('=', 1)[1].strip() for line in (root / 'gradle.properties').read_text().splitlines() if line.startswith('pluginVersion='))
core = root / f'plugins/core/build/distributions/core-{version}.zip'
riscv = root / f'plugins/arch-riscv/build/distributions/arch-riscv-{version}.zip'
linker = root / f'plugins/linker-script/build/distributions/linker-script-{version}.zip'
modes = {'core-only': (core,), 'core-riscv': (core, riscv), 'core-ld': (core, linker), 'all': (core, riscv, linker), 'extension': (core,)}
selected_modes = {args.mode: modes[args.mode]} if args.mode else modes
for artifact in {artifact for artifacts in selected_modes.values() for artifact in artifacts}:
    if not artifact.is_file():
        raise SystemExit(f'Build plugin ZIPs first: missing {artifact}')
classes = work / 'classes'
classes.mkdir(exist_ok=True)
classpath = os.pathsep.join([str(ide / 'lib' / '*'), str(root / f'plugins/core/build/libs/core-{version}.jar')])
subprocess.run(['javac', '-proc:none', '-cp', classpath, '-d', str(classes), *[str(p) for p in sorted((root / 'tools/smoke').glob('*.java'))]], check=True)
failures = []
for mode, artifacts in selected_modes.items():
    sandbox = work / mode
    # Fresh, task-owned directories only; never use the user's IDE profile.
    if sandbox.exists():
        if args.reuse_config:
            shutil.rmtree(sandbox / 'plugins', ignore_errors=True)
        else:
            shutil.rmtree(sandbox)
    plugins = sandbox / 'plugins'
    plugins.mkdir(parents=True)
    for artifact in artifacts:
        with zipfile.ZipFile(artifact) as archive:
            archive.extractall(plugins)
    smoke_lib = plugins / 'smoke' / 'lib'
    smoke_lib.mkdir(parents=True)
    with zipfile.ZipFile(smoke_lib / 'smoke.jar', 'w') as archive:
        for compiled in classes.rglob('*.class'):
            archive.write(compiled, compiled.relative_to(classes))
        descriptor = '''<idea-plugin>
<id>dev.masmld.smoke</id><name>MASMLD Smoke Tests</name><version>0.1.0</version><vendor>MASMLD</vendor>
<depends>com.intellij.modules.platform</depends><depends>dev.masmld.core</depends>
<extensions defaultExtensionNs="com.intellij"><appStarter id="masmld-smoke" implementation="dev.masmld.smoke.SmokeStarter"/></extensions>
</idea-plugin>'''
        if mode == 'extension':
            descriptor = descriptor.replace('</idea-plugin>', '''<extensions defaultExtensionNs="dev.masmld.core"><architecture implementation="dev.masmld.smoke.SampleArchitectureProvider"/></extensions>
<extensions defaultExtensionNs="com.intellij"><lang.parserDefinition language="MASMLD Smoke Example" implementationClass="dev.masmld.smoke.SampleParserDefinition"/></extensions></idea-plugin>''')
        archive.writestr('META-INF/plugin.xml', descriptor)
    options = (ide / 'bin/rustrover64.vmoptions').read_text()
    for key, value in {
        'idea.config.path': sandbox / 'config', 'idea.system.path': sandbox / 'system',
        'idea.plugins.path': plugins, 'idea.log.path': sandbox / 'log',
        'java.awt.headless': 'true',
        'masmld.smoke.riscv': str(mode in ('core-riscv', 'all')).lower(),
        'masmld.smoke.ld': str(mode in ('core-ld', 'all')).lower(),
        'masmld.smoke.extension': str(mode == 'extension').lower(),
        'masmld.smoke.file': sandbox / 'smoke.s',
    }.items():
        options += f'\n-D{key}={value}\n'
    vmoptions = sandbox / 'smoke.vmoptions'
    vmoptions.write_text(options)
    env = dict(os.environ, RUSTROVER_VM_OPTIONS=str(vmoptions))
    log = sandbox / 'stdout.log'
    with log.open('w') as output:
        result = subprocess.run([str(ide / 'bin/rustrover.sh'), 'masmld-smoke'],
                                env=env, stdout=output, stderr=subprocess.STDOUT, timeout=120)
    content = log.read_text(errors='replace')
    print('\n'.join(line for line in content.splitlines() if line.startswith(('PASS:', 'MASMLD_SMOKE_OK'))))
    if result.returncode != 0 or 'MASMLD_SMOKE_OK' not in content:
        print(content[-10000:])
        failures.append(f'{mode} failed ({result.returncode}); see {log}')
    else:
        print(f'{mode}: passed; log: {log}')
if failures:
    raise SystemExit('\n'.join(failures))
