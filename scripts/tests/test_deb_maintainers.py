# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
"""Exercise DEB launcher scripts in an isolated filesystem, without root."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class DebMaintainerTest(unittest.TestCase):
    def setUp(self):
        """Create an isolated launcher containing non-ASCII text.

        Automatically remove the temporary tree after each test.
        """
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.source = self.root / 'opt/oscar-ui/lib/oscar-ui-OSCAR-UI.desktop'
        self.source.parent.mkdir(parents=True)
        self.source.write_text('[Desktop Entry]\nName=OSCAR-UI café\n', encoding='utf-8')
        self.target = self.root / 'usr/share/applications/oscar-ui-OSCAR-UI.desktop'

    def run_script(self, name, operation):
        """Run a named maintainer script against the temporary filesystem.

        Pass the requested operation and return its exit status and UTF-8 output.
        """
        # Redirect only installation prefixes; execute real install/rm in the fixture.
        script = (ROOT / 'packaging/linux' / name).read_text(encoding='utf-8')
        script = script.replace('/opt/', str(self.root) + '/opt/')
        script = script.replace('/usr/share/', str(self.root) + '/usr/share/')
        return subprocess.run(['sh', '-c', script, name, operation],
                              capture_output=True, text=True, encoding='utf-8')

    def test_headless_install_and_remove_preserve_unrelated_files(self):
        result = self.run_script('postinst', 'configure')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.target.read_bytes(), self.source.read_bytes())
        self.assertEqual(self.target.stat().st_mode & 0o777, 0o644)
        self.assertEqual(self.target.parent.stat().st_mode & 0o777, 0o755)
        other = self.target.with_name('other.desktop')
        other.write_text('keep café', encoding='utf-8')
        self.assertEqual(self.run_script('prerm', 'remove').returncode, 0)
        self.assertFalse(self.target.exists())
        self.assertEqual(other.read_text(encoding='utf-8'), 'keep café')
        self.assertEqual(self.run_script('prerm', 'remove').returncode, 0)

    def test_missing_desktop_directory_does_not_block_removal(self):
        self.assertEqual(self.run_script('prerm', 'remove').returncode, 0)

    def test_upgrade_and_aborted_removal_restore_launcher(self):
        result = self.run_script('postinst', 'configure')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.run_script('prerm', 'upgrade').returncode, 0)
        self.assertFalse(self.target.exists())
        self.assertEqual(self.run_script('postinst', 'abort-remove').returncode, 0)
        self.assertTrue(self.target.exists())

    def test_install_failure_is_not_suppressed(self):
        self.source.unlink()
        self.assertNotEqual(self.run_script('postinst', 'configure').returncode, 0)

    def test_unknown_operation_fails(self):
        self.assertNotEqual(self.run_script('prerm', 'unexpected').returncode, 0)
