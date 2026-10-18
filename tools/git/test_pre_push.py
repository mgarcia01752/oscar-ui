# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
"""Verify pre-push enforcement using disposable repositories and a fixture checker."""
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

SOURCE = Path(__file__).resolve().parents[2]


class PrePushTest(unittest.TestCase):
    """
    Exercise real Git hooks without running the application's build in fixtures.
    """

    def setUp(self):
        """
        Prepare a clean committed checkout and a private bare remote.
        """
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.base = Path(self.temporary.name)
        self.root = self.base / 'checkout'
        (self.root / 'tools/git/hooks').mkdir(parents=True)
        (self.root / 'tools/development').mkdir()
        for name in ('tools/git/pre-push.py', 'tools/git/install-hooks.py', 'tools/git/hooks/pre-push'):
            shutil.copy2(SOURCE / name, self.root / name)
        (self.root / 'tools/development/pre-push-checks.py').write_text(
            "import os, sys\nprint('Ran fixture validation')\n"
            "sys.exit(int(os.environ.get('FIXTURE_CHECK_EXIT', '0')))\n", encoding='utf-8')
        self.git('init', '-b', 'main')
        self.git('config', 'user.name', 'Hook test')
        self.git('config', 'user.email', 'hook@example.invalid')
        self.git('add', '.')
        self.git('commit', '-m', 'Fixture')
        self.remote = self.base / 'origin.git'
        subprocess.run(['git', 'init', '--bare', str(self.remote)], check=True, capture_output=True)
        self.git('remote', 'add', 'origin', str(self.remote))

    def git(self, *arguments):
        """
        Run Git in the disposable checkout and return decoded output.
        """
        return subprocess.run(['git', '-C', str(self.root), *arguments], check=True,
                              capture_output=True, text=True, encoding='utf-8').stdout.strip()

    def install(self, success=True):
        """
        Run the installer from another directory and assert its result.
        """
        result = subprocess.run([sys.executable, str(self.root / 'tools/git/install-hooks.py')],
                                cwd=self.base, capture_output=True, text=True, encoding='utf-8')
        self.assertEqual(result.returncode == 0, success, result.stderr)
        return result

    def push(self, *refs, success=True, failure=False):
        """
        Push to the fixture remote with a controllable checker exit status.
        """
        environment = os.environ.copy()
        environment['FIXTURE_CHECK_EXIT'] = '7' if failure else '0'
        result = subprocess.run(['git', '-C', str(self.root), 'push', 'origin', *refs],
                                env=environment, capture_output=True, text=True, encoding='utf-8')
        self.assertEqual(result.returncode == 0, success, result.stdout + result.stderr)
        return result

    def test_successful_branch_and_tag_pushes_run_checks(self):
        """
        Both ordinary branch pushes and annotated tag pushes invoke the hook.
        """
        self.install()
        self.install()
        self.assertIn('Ran fixture validation', self.push('main').stdout)
        self.git('tag', '-a', 'v1.0.0', '-m', 'Release')
        self.assertIn('Ran fixture validation', self.push('v1.0.0').stdout)
        self.assertIn('refs/tags/v1.0.0', self.git('ls-remote', 'origin'))

    def test_failed_checks_block_remote_update(self):
        """
        A checker failure prevents any branch upload.
        """
        self.install()
        self.push('main', success=False, failure=True)
        self.assertEqual(self.git('ls-remote', 'origin'), '')

    def test_dirty_checkout_is_rejected(self):
        """
        Uncommitted files cannot masquerade as verification of committed code.
        """
        self.install()
        (self.root / 'unfinished.txt').write_text('pending', encoding='utf-8')
        result = self.push('main', success=False)
        self.assertIn('Commit or stash', result.stderr)
        self.assertEqual(self.git('ls-remote', 'origin'), '')

    def test_tag_on_another_commit_is_rejected(self):
        """
        Do not validate HEAD while uploading an unrelated tag's commit.
        """
        self.install()
        self.git('tag', 'old')
        self.git('commit', '--allow-empty', '-m', 'New head')
        result = self.push('old', success=False)
        self.assertIn('not the checked-out commit', result.stderr)
        self.assertEqual(self.git('ls-remote', 'origin'), '')

    def test_existing_hooks_are_preserved(self):
        """
        Never hide or replace an unrelated existing Git hook.
        """
        hook = self.root / '.git/hooks/pre-commit'
        hook.write_text('#!/bin/sh\nexit 0\n', encoding='utf-8')
        self.install(success=False)
        self.assertEqual(hook.read_text(encoding='utf-8'), '#!/bin/sh\nexit 0\n')
        self.assertEqual(self.git('config', '--get-regexp', '^core\\.' ).count('hookspath'), 0)

    def test_existing_hooks_path_is_preserved(self):
        """
        Existing repository hook configuration requires explicit integration.
        """
        self.git('config', '--local', 'core.hooksPath', 'other-hooks')
        self.install(success=False)
        self.assertEqual(self.git('config', '--get', 'core.hooksPath'), 'other-hooks')


if __name__ == '__main__':
    unittest.main()
