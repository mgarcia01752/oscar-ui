#!/usr/bin/env python3
# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
"""Remove Jammy's indirect PCRE1 dependency after inspecting the DEB payload."""
import argparse
from pathlib import Path
import re
import subprocess
import tempfile


def normalize(package):
    """Normalize a generated DEB atomically without installing it.

    Retain all dependencies except libpcre3, which jpackage discovers through
    the build host's GLib. Refuse removal if a payload ELF needs PCRE1 directly
    or the package does not declare GLib. Requires dpkg-deb and readelf.
    """
    with tempfile.TemporaryDirectory(prefix='oscar-deb-', dir=package.parent) as directory:
        workspace = Path(directory)
        payload = workspace / 'payload'
        subprocess.run(['dpkg-deb', '-R', str(package), str(payload)], check=True)
        control = payload / 'DEBIAN/control'
        original = control.read_text(encoding='utf-8')
        match = re.search(r'^Depends: (.*(?:\n[ \t]+.*)*)$', original, re.MULTILINE)
        if not match:
            raise ValueError('DEB has no Depends field')
        dependencies = [entry.strip() for entry in match[1].replace('\n', ' ').split(',')]
        if not any(re.fullmatch(r'libpcre3(?: \([^)]*\))?', entry) for entry in dependencies):
            print('[DEB] No indirect libpcre3 dependency to remove.')
            return
        if not any(re.match(r'libglib2\.0-0(?:t64)?(?: |$)', entry) for entry in dependencies):
            raise ValueError('Refusing to remove libpcre3 without a GLib dependency')
        for binary in payload.rglob('*'):
            if binary.is_symlink() or not binary.is_file():
                continue
            with binary.open('rb') as stream:
                is_elf = stream.read(4) == b'\x7fELF'
            if not is_elf:
                continue
            dynamic = subprocess.run(['readelf', '-d', str(binary)], check=True,
                                     capture_output=True, text=True, encoding='utf-8').stdout
            if re.search(r'\(NEEDED\).*\[libpcre\.so\.', dynamic):
                raise ValueError(f'Payload directly requires PCRE1: {binary.relative_to(payload)}')
        dependencies = [entry for entry in dependencies
                        if not re.fullmatch(r'libpcre3(?: \([^)]*\))?', entry)]
        control.write_text(original[:match.start()] + 'Depends: ' + ', '.join(dependencies)
                           + original[match.end():], encoding='utf-8')
        rebuilt = workspace / 'normalized.deb'
        subprocess.run(['dpkg-deb', '--root-owner-group', '-b', str(payload), str(rebuilt)], check=True)
        rebuilt.replace(package)
        print('[DEB] Removed indirect libpcre3 dependency; retained GLib and other requirements.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('package', type=Path)
    normalize(parser.parse_args().package.resolve())
