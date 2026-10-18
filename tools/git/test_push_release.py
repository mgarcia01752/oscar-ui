# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


class PushReleaseTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.base = Path(self.temporary.name)
        self.root = self.base / 'checkout'
        (self.root / 'tools/git').mkdir(parents=True)
        source = Path(__file__).resolve().parents[2]
        for name in ('tools/git/push-release.sh', 'tools/git/push-release.py', 'tools/git/install-hooks.py', 'tools/git/pre-push.py'):
            shutil.copy2(source / name, self.root / name)
        (self.root / 'tools/git/hooks').mkdir()
        shutil.copy2(source / 'tools/git/hooks/pre-push', self.root / 'tools/git/hooks/pre-push')
        (self.root / 'tools/development').mkdir()
        (self.root / 'tools/development/pre-push-checks.py').write_text(
            "print('Fixture checks passed')\n", encoding='utf-8')
        (self.root / 'pom.xml').write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><version>0.1.0</version><dependencies><dependency><version>0.1.0</version></dependency></dependencies></project>')
        self.git('init', '-b', 'main')
        self.git('config', 'user.name', 'Release tests')
        self.git('config', 'user.email', 'release@example.invalid')
        self.git('add', '.')
        self.git('commit', '-m', 'Initial')
        self.remote = self.base / 'remote.git'
        subprocess.run(['git', 'init', '--bare', str(self.remote)], check=True, capture_output=True)
        self.git('remote', 'add', 'origin', str(self.remote))
        self.git('push', '-u', 'origin', 'main')

    def git(self, *args):
        return subprocess.run(['git', '-C', str(self.root), *args], check=True, capture_output=True, text=True).stdout.strip()

    def run_release(self, *args, success=True):
        result = subprocess.run(['bash', str(self.root / 'tools/git/push-release.sh'), *args], cwd=self.base, capture_output=True, text=True)
        self.assertEqual(result.returncode == 0, success, result.stdout + result.stderr)
        return result

    def test_patch_commits_and_atomically_pushes_matching_tag(self):
        self.run_release()
        self.assertIn('<version>0.1.1</version>', (self.root / 'pom.xml').read_text())
        self.assertIn('<dependency><version>0.1.0</version>', (self.root / 'pom.xml').read_text())
        self.assertEqual(self.git('cat-file', '-t', 'v0.1.1'), 'tag')
        self.assertEqual(self.git('rev-parse', 'HEAD'), self.git('rev-parse', 'v0.1.1^{commit}'))
        refs = self.git('ls-remote', 'origin', 'refs/heads/main', 'refs/tags/v0.1.1^{}')
        self.assertEqual(refs.count(self.git('rev-parse', 'HEAD')), 2)
        self.assertEqual(self.git('status', '--porcelain'), '')

    def test_preview_changes_nothing(self):
        before = self.git('rev-parse', 'HEAD')
        result = self.run_release('--push-minor', '--dry-run')
        self.assertIn('0.1.0 -> 0.2.0', result.stdout)
        self.assertEqual(self.git('rev-parse', 'HEAD'), before)
        self.assertEqual(self.git('tag'), '')
        self.assertEqual(self.git('status', '--porcelain'), '')

    def test_push_flags_select_version_increment(self):
        for flag, expected in [('--push-major', '1.0.0'), ('--push-minor', '0.2.0'), ('--push-patch', '0.1.1')]:
            with self.subTest(flag=flag):
                result = self.run_release(flag, '--dry-run')
                self.assertIn('0.1.0 -> ' + expected, result.stdout)
        self.assertEqual(self.git('status', '--porcelain'), '')

    def test_rejects_conflicting_push_flags(self):
        self.run_release('--push-major', '--push-minor', success=False)
        self.run_release('--push-patch', '--version', '1.0.0', success=False)
        self.assertEqual(self.git('tag'), '')

    def test_failed_preflight_does_not_bump_commit_tag_or_push(self):
        """
        Release preparation stops before version mutation if local checks fail.
        """
        checker = self.root / 'tools/development/pre-push-checks.py'
        checker.write_text('raise SystemExit(9)\n', encoding='utf-8')
        self.git('add', '.')
        self.git('commit', '-m', 'Failing fixture checker')
        before = self.git('rev-parse', 'HEAD')
        original_pom = (self.root / 'pom.xml').read_bytes()
        self.run_release(success=False)
        self.assertEqual(self.git('rev-parse', 'HEAD'), before)
        self.assertEqual(self.git('tag'), '')
        self.assertEqual((self.root / 'pom.xml').read_bytes(), original_pom)
        self.assertEqual(self.git('status', '--porcelain'), '')

    def test_explicit_version(self):
        self.run_release('--version', '1.2.3')
        self.assertEqual(self.git('tag'), 'v1.2.3')

    def test_dirty_tree_is_rejected(self):
        (self.root / 'notes.txt').write_text('unfinished')
        self.run_release(success=False)
        self.assertEqual(self.git('tag'), '')

    def test_invalid_or_old_version_is_rejected(self):
        for version in ('0.1.0', '0.0.9', 'v0.1.1', '01.2.3'):
            self.run_release('--version', version, success=False)
        self.assertEqual(self.git('status', '--porcelain'), '')

    def test_existing_remote_tag_is_rejected(self):
        self.git('tag', 'v0.1.1')
        self.git('push', 'origin', 'v0.1.1')
        self.git('tag', '-d', 'v0.1.1')
        self.run_release(success=False)
        self.assertEqual(self.git('status', '--porcelain'), '')

    def test_existing_local_tag_is_not_moved(self):
        self.git('tag', 'v0.1.1')
        before = self.git('rev-parse', 'v0.1.1')
        self.run_release(success=False)
        self.assertEqual(before, self.git('rev-parse', 'v0.1.1'))
        self.assertEqual(self.git('status', '--porcelain'), '')

    def test_detached_head_is_rejected(self):
        self.git('checkout', '--detach')
        self.run_release(success=False)
        self.assertEqual(self.git('tag'), '')

    def test_remote_ahead_is_rejected_before_version_edit(self):
        old = self.git('rev-parse', 'HEAD')
        self.git('commit', '--allow-empty', '-m', 'Remote advancement')
        self.git('push', 'origin', 'main')
        self.git('reset', '--hard', old)
        result = self.run_release(success=False)
        self.assertIn('behind or diverged', result.stderr)
        self.assertEqual(self.git('rev-parse', 'HEAD'), old)
        self.assertEqual(self.git('status', '--porcelain'), '')

    def test_rejected_push_retains_commit_and_tag_for_retry(self):
        hook = self.remote / 'hooks/pre-receive'
        hook.write_text('#!/bin/sh\nexit 1\n')
        hook.chmod(0o755)
        previous = self.git('rev-parse', 'HEAD')
        result = self.run_release(success=False)
        self.assertIn('Retry without bumping', result.stderr)
        self.assertEqual(self.git('tag'), 'v0.1.1')
        self.assertIn(previous, self.git('ls-remote', 'origin', 'refs/heads/main'))
        self.assertNotIn('refs/tags/v0.1.1', self.git('ls-remote', 'origin'))


if __name__ == '__main__':
    unittest.main()
