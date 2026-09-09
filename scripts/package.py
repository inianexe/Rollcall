"""Build and validate an extension-only archive using Python's standard library."""
from pathlib import Path
import hashlib
import json
from zipfile import ZipFile, ZIP_DEFLATED

ROOT = Path(__file__).resolve().parents[1]
EXT = ROOT / 'extension'
manifest = json.loads((EXT / 'manifest.json').read_text())
version = manifest['version']
assert json.loads((ROOT / 'package.json').read_text())['version'] == version
assert manifest['manifest_version'] == 3
required = ['manifest.json'] + list(manifest['icons'].values())
for script in manifest['content_scripts']:
    required += script['js']
for name in required:
    path = (EXT / name).resolve()
    assert path.is_relative_to(EXT.resolve()) and path.is_file(), f'Missing/unsafe file: {name}'
files = [p for p in EXT.rglob('*') if p.is_file() and 'tests' not in p.relative_to(EXT).parts]
out = ROOT / 'dist'
out.mkdir(exist_ok=True)
archive = out / f'rollcall-v{version}-browser.zip'
with ZipFile(archive, 'w', ZIP_DEFLATED) as z:
    for path in sorted(files):
        z.write(path, 'rollcall-extension/' + path.relative_to(EXT).as_posix())
with ZipFile(archive) as z:
    names = z.namelist()
    assert z.testzip() is None
    assert all('rollcall-extension/' + name in names for name in required)
    assert sum(n.endswith('/manifest.json') for n in names) == 1
    assert not any('/tests/' in n or '/android/' in n for n in names)
digest = hashlib.sha256(archive.read_bytes()).hexdigest()
(out / 'SHA256SUMS.txt').write_text(f'{digest}  {archive.name}\n')
print(f'PASS: {archive.name}: {len(files)} files, manifest and all declared resources present')
print(f'SHA256: {digest}')
