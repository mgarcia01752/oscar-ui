# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
"""Verify header maintenance preserves source and third-party ownership."""
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location('headers', Path(__file__).with_name('check-license-headers.py'))
HEADERS = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(HEADERS)


class LicenseHeadersTest(unittest.TestCase):
    def test_preserves_shebang_encoding_bom_and_newlines(self):
        """Keep interpreter metadata and byte conventions while adding a header.

        Verify a second fix leaves the source byte-for-byte unchanged.
        """
        for suffix, original in [('.java', b'package example;\n'),
                                 ('.sh', b'#!/bin/sh\necho ok\n'),
                                 ('.py', b'#!/usr/bin/python3\n# coding: utf-8\npass\n'),
                                 ('.ps1', b'\xef\xbb\xbfWrite-Host ok\r\n')]:
            with self.subTest(suffix=suffix):
                fixed, error = HEADERS._header(original, suffix, 2026)
                self.assertIsNone(error)
                self.assertIn(b'SPDX-License-Identifier: Apache-2.0', fixed)
                if original.startswith(b'#!'):
                    self.assertEqual(original.splitlines()[0], fixed.splitlines()[0])
                if b'coding:' in original:
                    self.assertEqual(original.splitlines()[1], fixed.splitlines()[1])
                if original.startswith(b'\xef\xbb\xbf'):
                    self.assertTrue(fixed.startswith(b'\xef\xbb\xbf'))
                    self.assertNotIn(b'\n', fixed.replace(b'\r\n', b''))
                self.assertEqual((fixed, None), HEADERS._header(fixed, suffix, 2027))

    def test_refuses_existing_other_or_incomplete_notices(self):
        """Do not relicense third-party source or overwrite attribution.

        Partial headers require a deliberate manual ownership review.
        """
        for original in (b'# Copyright 2020 Someone Else\npass\n',
                         b'# SPDX-License-Identifier: MIT\npass\n',
                         b'/* Licensed under another license. */\npackage example;\n'):
            self.assertEqual(original, HEADERS._header(original, '.py', 2026)[0])
            self.assertIsNotNone(HEADERS._header(original, '.py', 2026)[1])

    def test_scopes_files_and_skips_ignored_vendor_content(self):
        """Discover maintained source plus new files without traversing caches.

        Maven Wrapper and dependency directories retain their own notices.
        """
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            subprocess.run(['git', 'init', '-q', str(root)], check=True)
            (root / '.gitignore').write_text('tools/cache/\n', encoding='utf-8')
            names = ['src/main/java/App.java', 'scripts/unix/run.sh', 'tools/new.py',
                     'tools/cache/generated.py', 'mvnw', '.tools/vendor.java', 'licenses/sample.py']
            for name in names:
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text('', encoding='utf-8')
            found = {path.relative_to(root).as_posix() for path in HEADERS._source_files(root)}
            self.assertEqual(set(names[:3]), found)


if __name__ == '__main__':
    unittest.main()
