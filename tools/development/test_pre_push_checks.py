"""Regression checks for the host validation runner without invoking builds."""
import importlib.util
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

SOURCE = Path(__file__).with_name('pre-push-checks.py')
SPEC = importlib.util.spec_from_file_location('pre_push_checks', SOURCE)
CHECKS = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CHECKS)


class PrePushChecksTest(unittest.TestCase):
    """
    Check failure propagation, UTF-8 handling, and fixture environment isolation.
    """

    def test_missing_required_tool_blocks_checks(self):
        """
        Do not silently omit a required validator when it is unavailable.
        """
        with patch.object(CHECKS.shutil, 'which', return_value=None):
            with self.assertRaisesRegex(RuntimeError, 'is required'):
                CHECKS._tool('missing-validator')

    def test_git_hook_repository_environment_is_not_inherited(self):
        """
        Nested Git fixtures must not accidentally operate on the real checkout.
        """
        with patch.dict(os.environ, {'GIT_DIR': '/wrong/repository', 'GIT_INDEX_FILE': '/wrong/index', 'KEEP_ME': 'yes'}):
            environment = CHECKS._environment()
        self.assertNotIn('GIT_DIR', environment)
        self.assertNotIn('GIT_INDEX_FILE', environment)
        self.assertEqual(environment['KEEP_ME'], 'yes')

    def test_failed_stage_stops_pipeline(self):
        """
        A failed check prevents later bootstrap/build operations.
        """
        with patch.object(CHECKS, '_tool', return_value='/fixture/tool'), \
                patch.object(CHECKS, '_files', return_value=[]), \
                patch.object(CHECKS, '_run', side_effect=subprocess.CalledProcessError(1, 'fixture')) as run:
            with self.assertRaises(subprocess.CalledProcessError):
                CHECKS._checks()
        self.assertEqual(run.call_count, 1)

    def test_utf8_links_and_example_coverage(self):
        """
        Validate Unicode documents and fail when an example loses its catalog link.
        """
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'cm-config/examples').mkdir(parents=True)
            readme = root / 'cm-config/README.md'
            example = root / 'cm-config/examples/sample.txt'
            example.write_text('synthetic', encoding='utf-8')
            readme.write_text('# Examples\n“UTF-8” → [Sample](examples/sample.txt)\n', encoding='utf-8')
            with patch.object(CHECKS, 'ROOT', root):
                CHECKS._documentation([readme, example])
                readme.write_text('# Examples\n', encoding='utf-8')
                with self.assertRaisesRegex(RuntimeError, 'Example missing'):
                    CHECKS._documentation([readme, example])


if __name__ == '__main__':
    unittest.main()
