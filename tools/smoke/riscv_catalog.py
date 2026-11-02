#!/usr/bin/env python3
"""Assemble the actual new ISA documentation examples for both XLENs with LLVM MC."""
import csv
import json
import hashlib
from pathlib import Path
import shutil
import subprocess

root = Path(__file__).resolve().parents[2]
version = next(line.split('=', 1)[1].strip() for line in (root / 'gradle.properties').read_text().splitlines() if line.startswith('pluginVersion='))
script_hash = hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
output = root / 'build/riscv-catalog-smoke'
output.mkdir(parents=True, exist_ok=True)
if shutil.which('llvm-mc') is None:
    raise SystemExit('llvm-mc unavailable; ISA example integration not executed')
rows = list(csv.reader((root / 'plugins/arch-riscv/src/main/resources/catalog/riscv/extensions.tsv').open(), delimiter='\t'))
rows = [row for row in rows if row and not row[0].startswith('#')]
results = {}
for width in (32, 64):
    selected = [row for row in rows if int(row[4]) in (0, width)]
    lines = ['.text']
    for index, row in enumerate(selected):
        label = f'.Lexample_{index}'
        lines.extend([row[5].replace('label', label), label + ':'])
    source = output / f'examples-rv{width}.s'
    source.write_text('\n'.join(lines) + '\n')
    subprocess.run(['llvm-mc', f'-triple=riscv{width}', '-mattr=+m,+a,+f,+d,+c,+zicsr,+zifencei', '-filetype=obj', str(source), '-o', str(output / f'examples-rv{width}.o')], check=True, timeout=30)
    results[f'rv{width}'] = len(selected)
(output / 'result.json').write_text(json.dumps({'version': version, 'script_sha256': script_hash, 'examples': results}, indent=2) + '\n')
print('Actual ISA documentation examples assembled:', results)
