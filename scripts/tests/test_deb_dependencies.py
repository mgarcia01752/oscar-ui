# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
"""Exercise dependency normalization on real disposable DEBs without root."""
import importlib.util
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'build/deb-dependencies.py'
SPEC = importlib.util.spec_from_file_location('deb_dependencies', SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


@unittest.skipUnless(all(shutil.which(tool) for tool in ('dpkg-deb', 'readelf', 'cc')),
                     'Linux DEB and compiler tools required')
class DebDependenciesTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.payload = self.root / 'payload'
        (self.payload / 'DEBIAN').mkdir(parents=True)
        (self.payload / 'opt').mkdir()
        (self.payload / 'opt/keep.txt').write_text('preserve payload\n')
        self.package = self.root / 'test.deb'

    def build(self, dependencies):
        (self.payload / 'DEBIAN/control').write_text(
            'Package: oscar-ui\nVersion: 1.0\nArchitecture: amd64\n'
            'Maintainer: Test <test@example.com>\nDescription: Synthetic test\n'
            f'Depends: {dependencies}\n')
        subprocess.run(['dpkg-deb', '--root-owner-group', '-b', str(self.payload),
                        str(self.package)], check=True, capture_output=True)

    def test_removes_only_indirect_pcre_and_preserves_payload(self):
        self.build('libc6, libglib2.0-0 (>= 2.0), libpcre3 (>= 8.0), libpcre2-8-0')
        MODULE.normalize(self.package)
        dependencies = subprocess.check_output(
            ['dpkg-deb', '-f', str(self.package), 'Depends'], text=True)
        self.assertEqual(dependencies.strip(), 'libc6, libglib2.0-0 (>= 2.0), libpcre2-8-0')
        extracted = self.root / 'extracted'
        subprocess.run(['dpkg-deb', '-x', str(self.package), str(extracted)], check=True)
        self.assertEqual((extracted / 'opt/keep.txt').read_text(), 'preserve payload\n')
        before = self.package.read_bytes()
        MODULE.normalize(self.package)
        self.assertEqual(self.package.read_bytes(), before)

    def test_missing_glib_keeps_original_package(self):
        self.build('libc6, libpcre3')
        before = self.package.read_bytes()
        with self.assertRaisesRegex(ValueError, 'without a GLib'):
            MODULE.normalize(self.package)
        self.assertEqual(self.package.read_bytes(), before)

    def test_direct_pcre_dependency_keeps_original_package(self):
        source = self.root / 'library.c'
        source.write_text('int synthetic_pcre(void) { return 0; }\n')
        library = self.root / 'libpcre.so.3'
        subprocess.run(['cc', '-shared', '-fPIC', '-Wl,-soname,libpcre.so.3',
                        str(source), '-o', str(library)], check=True)
        source.write_text('extern int synthetic_pcre(void);\n'
                          'int main(void) { return synthetic_pcre(); }\n')
        subprocess.run(['cc', str(source), str(library), '-o',
                        str(self.payload / 'opt/probe')], check=True)
        self.build('libglib2.0-0, libpcre3')
        before = self.package.read_bytes()
        with self.assertRaisesRegex(ValueError, 'directly requires PCRE1'):
            MODULE.normalize(self.package)
        self.assertEqual(self.package.read_bytes(), before)
