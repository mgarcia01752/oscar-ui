# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
"""Exercise cleanup in isolated repositories, never the real build workspace."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

SOURCE = Path(__file__).resolve().parents[2]


class CleanupTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name) / 'repo with spaces'
        for name in ('tools/development', 'scripts/unix', 'target', 'dist', '.tools', '.m2',
                     'cm-config', 'docs/images'):
            (self.root / name).mkdir(parents=True, exist_ok=True)
        for name in ('target/app', 'dist/image.msi', '.tools/qt', '.m2/cache',
                     'cm-config/customer.cfg', 'docs/images/guide.png'):
            (self.root / name).write_text('keep or generated')
        for name in ('tools/development/clean.py', 'scripts/unix/clean.sh'):
            shutil.copy2(SOURCE / name, self.root / name)
        subprocess.run(['git', 'init', '-q', str(self.root)], check=True)

    def run_clean(self, *args):
        return subprocess.run(['bash', str(self.root / 'scripts/unix/clean.sh'), *args],
                              cwd=self.temporary.name, text=True, capture_output=True)

    def test_preview_then_clean_from_other_directory_preserves_inputs(self):
        self.assertEqual(0, self.run_clean('--dry-run').returncode)
        self.assertTrue((self.root / 'target/app').exists())
        self.assertTrue((self.root / 'dist/image.msi').exists())
        self.assertEqual(0, self.run_clean().returncode)
        self.assertFalse((self.root / 'target').exists())
        self.assertFalse((self.root / 'dist').exists())
        for name in ('.tools/qt', '.m2/cache', 'cm-config/customer.cfg', 'docs/images/guide.png'):
            self.assertTrue((self.root / name).exists())
        self.assertEqual(0, self.run_clean().returncode)

    def test_dist_only_and_invalid_option(self):
        self.assertNotEqual(0, self.run_clean('--unknown').returncode)
        self.assertTrue((self.root / 'dist').exists())
        self.assertEqual(0, self.run_clean('--dist').returncode)
        self.assertTrue((self.root / 'target/app').exists())

    def test_refuses_tracked_content_before_any_removal(self):
        subprocess.run(['git', '-C', str(self.root), 'add', 'dist/image.msi'], check=True)
        self.assertNotEqual(0, self.run_clean().returncode)
        self.assertTrue((self.root / 'target/app').exists())

    def test_refuses_output_symlink(self):
        shutil.rmtree(self.root / 'dist')
        (self.root / 'dist').symlink_to(self.root / 'cm-config', target_is_directory=True)
        self.assertNotEqual(0, self.run_clean().returncode)
        self.assertTrue((self.root / 'target/app').exists())
        self.assertTrue((self.root / 'cm-config/customer.cfg').exists())
