#!/usr/bin/env python3
# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
"""Check or add SPDX headers to explicitly scoped, project-owned source files."""
import argparse
from datetime import date
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
HOLDER = 'OSCAR-UI contributors'
LICENSE = 'Apache-2.0'
SPECIAL_FILES = {'install.sh', 'tools/git/hooks/pre-push',
                 'packaging/linux/postinst', 'packaging/linux/prerm'}


def main():
    """Check repository source headers, optionally adding missing headers.

    Default to read-only checks. Never replace existing copyright or license
    declarations; report conflicts for manual review. Return nonzero on failure.
    """
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--fix', action='store_true', help='Add absent headers; preserve existing notices')
    arguments = parser.parse_args()
    failures = 0
    paths = _source_files(ROOT)
    for path in paths:
        original = path.read_bytes()
        updated, error = _header(original, path.suffix, date.today().year)
        relative = path.relative_to(ROOT)
        if error:
            print(f'[license] REVIEW {relative}: {error}')
            failures += 1
        elif updated != original:
            if arguments.fix:
                path.write_bytes(updated)
                print(f'[license] ADDED {relative}')
            else:
                print(f'[license] MISSING {relative}')
                failures += 1
    print(f'[license] Checked {len(paths)} source files; {failures} issue(s)')
    return 1 if failures else 0


def _source_files(root):
    output = subprocess.check_output(
        ['git', 'ls-files', '-z', '--cached', '--others', '--exclude-standard'], cwd=root)
    paths = []
    for name in sorted(set(output.decode('utf-8').split('\0')) - {''}):
        relative = Path(name)
        selected = (name in SPECIAL_FILES or
                    (relative.parts[0] == 'src' and relative.suffix == '.java') or
                    (relative.parts[0] in ('scripts', 'tools') and
                     relative.suffix in ('.py', '.sh', '.ps1')))
        path = root / relative
        if selected and path.is_file() and not path.is_symlink():
            paths.append(path)
    return paths


def _header(content, suffix, year):
    text = content.decode('utf-8-sig')
    # Only recognize declarations in leading comments, never fixture strings.
    leading = re.match(r'\A(?:\s|/\*[\s\S]*?\*/|//[^\n]*(?:\n|$)|\#[^\n]*(?:\n|$))*', text).group()
    copyright_line = re.search(r'Copyright\s+(\d{4}(?:-\d{4})?)\s+' + re.escape(HOLDER) + r'\.?\s*(?:\*/)?\s*$', leading, re.M)
    spdx = re.findall(r'SPDX-License-Identifier:\s*([^\s*]+)', leading)
    copyright_count = len(re.findall(r'Copyright', leading, re.I))
    if copyright_line and spdx == [LICENSE] and copyright_count == 1:
        return content, None
    if re.search(r'copyright|SPDX-License-Identifier|licensed under|license terms', leading, re.I):
        return content, 'Existing notice is incomplete or different; review without overwriting it'
    newline = '\r\n' if '\r\n' in text else '\n'
    notice = [f'Copyright {year} {HOLDER}', f'SPDX-License-Identifier: {LICENSE}']
    if suffix == '.java':
        header = newline.join(['/*', *(' * ' + line for line in notice), ' */', ''])
    else:
        header = newline.join([*('# ' + line for line in notice), ''])
    lines = text.splitlines(keepends=True)
    insert_at = 1 if lines and lines[0].startswith('#!') else 0
    # Keep Python encoding declarations within the first two lines.
    if suffix == '.py':
        for index, line in enumerate(lines[:2]):
            if re.match(r'^\s*#.*coding[:=]\s*[-\w.]+', line):
                insert_at = index + 1
    prefix = ''.join(lines[:insert_at])
    if prefix and not prefix.endswith('\n'):
        prefix += newline
    result = prefix + header + ''.join(lines[insert_at:])
    bom = b'\xef\xbb\xbf' if content.startswith(b'\xef\xbb\xbf') else b''
    return bom + result.encode('utf-8'), None


if __name__ == '__main__':
    raise SystemExit(main())
