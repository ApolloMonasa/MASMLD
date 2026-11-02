#!/usr/bin/env python3
"""Exercise the real LLVM CLI protocol used for debug-symbol navigation (Linux host)."""
import json
import hashlib
from pathlib import Path
import shutil
import subprocess

root = Path(__file__).resolve().parents[2]
version = next(line.split('=', 1)[1].strip() for line in (root / 'gradle.properties').read_text().splitlines() if line.startswith('pluginVersion='))
script_hash = hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
output = root / 'build/toolchain-smoke'
output.mkdir(parents=True, exist_ok=True)
for tool in ('cc', 'llvm-nm', 'llvm-symbolizer', 'llvm-cxxfilt'):
    if shutil.which(tool) is None:
        raise SystemExit(f'Required test tool unavailable: {tool}; integration not executed')
source = output / 'fixture.c'
source.write_text('extern int source_entry(void) __asm__("linked_entry");\nint source_entry(void) { return 42; }\nint main(void) { return source_entry(); }\n')
artifact = output / 'fixture'
subprocess.run(['cc', '-g', '-O0', '-fno-pie', '-no-pie', str(source), '-o', str(artifact)], check=True, timeout=30)
nm = subprocess.check_output(['llvm-nm', '--defined-only', '--format=posix', str(artifact)], text=True, timeout=10)
fields = next(line.split() for line in nm.splitlines() if line.startswith('linked_entry '))
address = '0x' + fields[2]
location = subprocess.check_output(['llvm-symbolizer', '--obj=' + str(artifact), '--inlining=false', address], text=True, timeout=10)
assert str(source) + ':2:' in location, location
demangled = subprocess.check_output(['llvm-cxxfilt', '_ZN3app3runEv'], text=True, timeout=10).strip()
assert demangled == 'app::run()', demangled
print('LLVM toolchain integration passed: linked export alias → debug source line; Itanium demangling.')

# Exercise C++ and Rust when their compilers are available; keep per-language evidence explicit.
languages = {}
for language, compiler, filename, contents, flags, export in [
    ('cpp', 'c++', 'fixture.cpp', 'namespace app { __attribute__((noinline)) int run() { return 7; } }\nint main() { return app::run(); }\n', [], '_ZN3app3runEv'),
    ('rust', 'rustc', 'fixture.rs', '#[unsafe(export_name="rust_entry")]\npub extern "C" fn source_entry() -> i32 { 42 }\n#[inline(never)] fn worker() -> i32 { source_entry() }\nfn main() { let _ = worker(); }\n', ['--edition=2024', '-C', 'symbol-mangling-version=v0'], 'rust_entry'),
]:
    if shutil.which(compiler) is None:
        languages[language] = {'executed': False, 'reason': 'compiler unavailable'}
        continue
    source_file = output / filename
    source_file.write_text(contents)
    binary = output / ('fixture-' + language)
    subprocess.run([compiler, '-g', *flags, str(source_file), '-o', str(binary)], check=True, timeout=60)
    symbols = subprocess.check_output(['llvm-nm', '--defined-only', '--format=posix', str(binary)], text=True, timeout=10)
    row = next(line.split() for line in symbols.splitlines() if line.startswith(export + ' '))
    resolved = subprocess.check_output(['llvm-symbolizer', '--obj=' + str(binary), '--inlining=false', '0x' + row[2]], text=True, timeout=10)
    assert str(source_file) + ':' in resolved, resolved
    languages[language] = {'executed': True, 'symbol': export, 'source': resolved.strip()}
    if language == 'rust':
        mangled = next(line.split()[0] for line in symbols.splitlines() if line.startswith('_R') and 'worker' in line)
        decoded = subprocess.check_output(['llvm-cxxfilt', mangled], text=True, timeout=10).strip()
        assert 'worker' in decoded and decoded != mangled, decoded
        languages[language]['v0_demangled'] = decoded
(output / 'language-results.json').write_text(json.dumps({'version': version, 'script_sha256': script_hash, 'languages': languages}, indent=2) + '\n')
(output / 'result.json').write_text(json.dumps({'version': version, 'script_sha256': script_hash, 'symbol': fields[0], 'address': address, 'location': location.strip(), 'demangled': demangled}, indent=2) + '\n')
print('Per-language toolchain checks:', ', '.join(f'{name}={result["executed"]}' for name, result in languages.items()))
