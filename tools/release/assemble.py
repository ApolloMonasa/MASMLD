#!/usr/bin/env python3
"""Audit and assemble local installable plugin ZIPs with checksums and verification evidence."""
import hashlib
import io
import json
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
VERSION = next(line.split('=', 1)[1].strip() for line in (ROOT / 'gradle.properties').read_text().splitlines() if line.startswith('pluginVersion='))
MODULES = {'core': 'dev.masmld.core', 'arch-riscv': 'dev.masmld.riscv', 'linker-script': 'dev.masmld.linker'}
output = ROOT / 'build/releases' / f'masmld-{VERSION}'
# Task-owned output only. A fresh directory avoids bundling older versions or evidence.
if output.exists():
    shutil.rmtree(output)
output.mkdir(parents=True)
manifest = {'version': VERSION, 'plugins': [], 'unit_tests': {}, 'binary_verification': [],
            'runtime_smoke': 'Platform editor tests execute in unit-test mode. See validation.md for the scope and version of publisher GUI acceptance.'}
for module, plugin_id in MODULES.items():
    artifact = ROOT / f'plugins/{module}/build/distributions/{module}-{VERSION}.zip'
    with zipfile.ZipFile(artifact) as archive:
        assert all(name.startswith(module + '/') and '..' not in Path(name).parts for name in archive.namelist()), f'Unexpected ZIP layout: {artifact}'
        jars = [name for name in archive.namelist() if name.endswith('.jar')]
        descriptors = []
        resources = set()
        for name in jars:
            with zipfile.ZipFile(io.BytesIO(archive.read(name))) as jar:
                names = jar.namelist()
                resources.update(names)
                assert not any(n.startswith('dev/masmld/smoke/') or n.endswith('Test.class') for n in names), 'Test code leaked into release'
                if 'META-INF/plugin.xml' in names:
                    descriptors.append(ET.fromstring(jar.read('META-INF/plugin.xml')))
                allowed = 'dev/masmld/' + {'core': 'core/', 'arch-riscv': 'riscv/', 'linker-script': 'ld/'}[module]
                assert all(n.startswith(allowed) for n in names if n.startswith('dev/masmld/') and n.endswith('.class')), 'Optional plugin code bundled into another module'
        required_icons = {'core': {'icons/assembly.svg', 'icons/assembly_dark.svg'}, 'linker-script': {'icons/linker.svg', 'icons/linker_dark.svg'}}
        assert required_icons.get(module, set()) <= resources, 'Missing file icons'
        assert {'META-INF/pluginIcon.svg', 'META-INF/pluginIcon_dark.svg', 'META-INF/LICENSE'} <= resources, 'Missing branding or MIT license'
        assert len(descriptors) == 1, 'Each ZIP must contain exactly one plugin descriptor'
        descriptor = descriptors[0]
        assert descriptor.findtext('id') == plugin_id
        assert descriptor.findtext('version') == VERSION
        dependencies = [node.text.strip() for node in descriptor.findall('depends')]
        expected = ['com.intellij.modules.platform'] + ([] if module == 'core' else ['dev.masmld.core'])
        assert sorted(dependencies) == sorted(expected), f'Unexpected plugin dependencies: {plugin_id}'
    shutil.copy2(artifact, output / artifact.name)
    manifest['plugins'].append({'id': plugin_id, 'file': artifact.name, 'depends': dependencies,
                                'sha256': hashlib.sha256(artifact.read_bytes()).hexdigest()})
    reports = sorted((ROOT / f'plugins/{module}/build/test-results/test').glob('TEST-*.xml'))
    assert reports, f'No unit-test evidence for {module}'
    count = 0
    for report in reports:
        result = ET.parse(report).getroot()
        assert int(result.attrib['failures']) == 0 and int(result.attrib['errors']) == 0 and int(result.attrib['skipped']) == 0, f'Failing/skipped tests: {report}'
        count += int(result.attrib['tests'])
        target = output / 'evidence/tests' / module
        target.mkdir(parents=True, exist_ok=True)
        shutil.copy2(report, target / report.name)
    manifest['unit_tests'][module] = count
    verdicts = list((ROOT / 'build/reports/pluginVerifier').glob(f'*/plugins/{plugin_id}/{VERSION}/verification-verdict.txt'))
    assert verdicts, f'No binary verification evidence for {plugin_id}'
    for verdict in verdicts:
        assert verdict.read_text().strip().startswith('Compatible'), f'Incompatible plugin: {verdict}'
        for issue in ('compatibility-problems.txt', 'internal-api-usages.txt', 'override-only-usages.txt', 'invalid-plugin.txt'):
            problem = verdict.parent / issue
            assert not problem.exists() or not problem.read_text().strip(), f'Blocking verifier report: {problem}'
        ide = verdict.parents[3].name
        destination = output / 'evidence/verifier' / ide / plugin_id
        destination.mkdir(parents=True, exist_ok=True)
        for report in verdict.parent.glob('*.txt'):
            shutil.copy2(report, destination / report.name)
        manifest['binary_verification'].append({'plugin': plugin_id, 'ide': ide, 'verdict': verdict.read_text().strip()})
manifest['toolchain_integration'] = {}
for label, script, folder in [('binary', 'binary.py', 'toolchain-smoke'), ('riscv_catalog', 'riscv_catalog.py', 'riscv-catalog-smoke')]:
    source_dir = ROOT / 'build' / folder
    report = source_dir / 'result.json'
    if report.exists():
        result = json.loads(report.read_text())
        fresh = result.get('version') == VERSION and result.get('script_sha256') == hashlib.sha256((ROOT / 'tools/smoke' / script).read_bytes()).hexdigest()
    else:
        fresh = False
    manifest['toolchain_integration'][label] = {'executed_for_current_version_and_script': fresh}
    if fresh:
        target = output / 'evidence/toolchains' / label
        target.mkdir(parents=True)
        for path in source_dir.iterdir():
            if path.suffix in ('.json', '.c', '.cpp', '.rs', '.s'):
                shutil.copy2(path, target / path.name)
(output / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
(output / 'INSTALL.md').write_text(f'''# MASMLD {VERSION}

1. Install core-{VERSION}.zip using Settings → Plugins → Install Plugin from Disk.
2. Optionally install arch-riscv-{VERSION}.zip and/or linker-script-{VERSION}.zip.
3. Restart the IDE. Keep Core and installed optional modules on the same version.
4. Select RISC-V in the project's MASMLD Assembly settings for assembly files.
5. GNU ld files use MASMLD Linker Script without an assembly architecture selection.

Enable Show quick documentation on hover, or use View → Quick Documentation.
File type associations already owned by another plugin may need manual selection.

These are installable local packages; consult the public release page for publication status. Marketplace review is a separate process.
Licensed under MIT; see LICENSE. Read validation.md for runtime checks and known gaps. SHA256SUMS covers every payload file.
''')
shutil.copy2(ROOT / 'docs/validation.md', output / 'validation.md')
shutil.copy2(ROOT / 'LICENSE', output / 'LICENSE')
shutil.copy2(ROOT / 'CHANGELOG.md', output / 'CHANGELOG.md')
shutil.copy2(ROOT / 'docs/developer-workflows.md', output / 'developer-workflows.md')
checksums = []
for path in sorted(output.rglob('*')):
    if path.is_file():
        checksums.append(f'{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.relative_to(output).as_posix()}')
(output / 'SHA256SUMS').write_text('\n'.join(checksums) + '\n')
archive_path = output.parent / (output.name + '.zip')
with zipfile.ZipFile(archive_path, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
    for path in sorted(output.rglob('*')):
        if path.is_file():
            archive.write(path, output.name + '/' + path.relative_to(output).as_posix())
print(f'Release bundle: {archive_path}')
print(f'Unit tests: {sum(manifest["unit_tests"].values())}; verified plugin/IDE pairs: {len(manifest["binary_verification"])}')
