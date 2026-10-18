# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
"""Exercise scoped pushes and the real hook against a disposable local remote."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest import mock

SOURCE = Path(__file__).resolve().parents[2]


class GitPushTest(unittest.TestCase):
    def setUp(self):
        """Create committed fixtures with a controllable local validation gate.

        No test accesses GitHub or invokes the full application build.
        """
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.base = Path(temporary.name)
        self.root = self.base / 'checkout'
        (self.root / 'tools/git/hooks').mkdir(parents=True)
        (self.root / 'tools/development').mkdir()
        for name in ('tools/git/git-push.sh', 'tools/git/pre-push.py', 'tools/git/install-hooks.py', 'tools/git/hooks/pre-push'):
            shutil.copy2(SOURCE / name, self.root / name)
        (self.root / 'tools/development/pre-push-checks.py').write_text(
            "import os, sys\nprint('FIXTURE_CHECKS_RAN', flush=True)\nsys.exit(int(os.environ.get('FIXTURE_EXIT', '0')))\n", encoding='utf-8')
        (self.root / 'pom.xml').write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><version>1.0.0</version></project>', encoding='utf-8')
        self._git('init', '-b', 'main')
        self._git('config', 'user.name', 'Push test')
        self._git('config', 'user.email', 'push@example.invalid')
        self._git('add', '.')
        self._git('commit', '-m', 'Fixture')
        subprocess.run(['git', 'init', '--bare', str(self.base / 'remote.git')], check=True, capture_output=True)
        self._git('remote', 'add', 'origin', str(self.base / 'remote.git'))
        self._git('tag', '-a', 'v1.0.0', '-m', 'Fixture')

    def test_branch_push_runs_checks_and_does_not_follow_tags(self):
        """Push only main even when Git is configured to follow annotated tags.

        Use the master option as the repository's primary-branch alias.
        """
        self._git('config', 'push.followTags', 'true')
        result = self._push('--master')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('FIXTURE_CHECKS_RAN', result.stdout)
        self.assertIn('refs/heads/main', self._git('ls-remote', 'origin'))
        self.assertNotIn('refs/tags', self._git('ls-remote', 'origin'))
        repeated = self._push('--master')
        self.assertIn('Nothing to push', repeated.stdout)
        self.assertNotIn('FIXTURE_CHECKS_RAN', repeated.stdout)

    def test_tag_push_runs_checks_and_leaves_branch_unpublished(self):
        """Publish only the requested tag after validating its checked-out commit.

        No remote branch is created as a side effect.
        """
        result = self._push('--tag', 'v1.0.0')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('FIXTURE_CHECKS_RAN', result.stdout)
        self.assertIn('refs/tags/v1.0.0', self._git('ls-remote', 'origin'))
        self.assertNotIn('refs/heads', self._git('ls-remote', 'origin'))
        repeated = self._push('--tag', 'v1.0.0')
        self.assertIn('Nothing to push', repeated.stdout)
        self.assertNotIn('FIXTURE_CHECKS_RAN', repeated.stdout)

    def test_failure_and_dirty_tree_block_upload(self):
        """Reject failed validation and uncommitted files without remote changes.

        A fixture environment variable controls only the test checker.
        """
        with mock.patch.dict(os.environ, {'FIXTURE_EXIT': '7'}):
            self.assertNotEqual(0, self._push('--master').returncode)
        (self.root / 'pending').write_text('pending', encoding='utf-8')
        self.assertNotEqual(0, self._push('--master').returncode)
        self.assertEqual('', self._git('ls-remote', 'origin'))

    def test_refuses_missing_tags_wrong_commit_and_feature_branch(self):
        """Reject unrelated refs before installing hooks or starting validation.

        Existing tags are never moved to satisfy a request.
        """
        self.assertNotEqual(0, self._push('--tag', 'v2.0.0').returncode)
        self._git('commit', '--allow-empty', '-m', 'Next')
        self.assertNotEqual(0, self._push('--tag', 'v1.0.0').returncode)
        self._git('checkout', '-b', 'feature')
        self.assertNotEqual(0, self._push('--master').returncode)
        self.assertEqual('', self._git('ls-remote', 'origin'))

    def test_release_bump_modes_delegate_and_propagate_failure(self):
        """Route each release option to the established helper without duplicating it.

        A fixture helper returns a failure so no release is created or uploaded.
        """
        helper = self.root / 'tools/git/push-release.sh'
        helper.write_text('#!/bin/bash\nprintf "RELEASE_ARGS:%s\\n" "$*"\nexit 7\n', encoding='utf-8')
        self._git('add', '.')
        self._git('commit', '-m', 'Release helper fixture')
        for bump in ('major', 'minor', 'patch'):
            result = self._push('--tag-' + bump)
            self.assertEqual(7, result.returncode, result.stderr)
            self.assertIn('RELEASE_ARGS:--push-' + bump, result.stdout)
        result = self._push('--master', '--tag-patch')
        self.assertEqual(2, result.returncode)
        self.assertNotIn('RELEASE_ARGS:', result.stdout)
        self.assertEqual('', self._git('ls-remote', 'origin'))

    def _git(self, *arguments):
        return subprocess.run(['git', '-C', str(self.root), *arguments], check=True,
                              capture_output=True, text=True, encoding='utf-8').stdout.strip()

    def _push(self, *arguments):
        return subprocess.run(['bash', str(self.root / 'tools/git/git-push.sh'), *arguments],
                              cwd=self.base, capture_output=True, text=True, encoding='utf-8')


if __name__ == '__main__':
    unittest.main()
