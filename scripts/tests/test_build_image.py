# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
"""Test native installer orchestration without running a real build."""
import importlib.util
from pathlib import Path
import subprocess
from types import SimpleNamespace
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location('build_image', Path(__file__).resolve().parents[1] / 'build/build-image.py')
BUILD = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(BUILD)


class BuildImageTest(unittest.TestCase):
    def test_selects_host_and_all_without_cross_compiling(self):
        """Route all to the host helper and reject foreign or unsupported hosts.

        Simulate the OS without changing pathlib's actual host implementation.
        """
        for host, target in [('linux', 'linux'), ('win32', 'windows')]:
            with self.subTest(host=host), patch.object(BUILD, 'sys', SimpleNamespace(platform=host)), patch.object(BUILD.platform, 'machine', return_value='AMD64'):
                self.assertEqual(target, BUILD._plan('all')[0])
                self.assertEqual(BUILD._plan(target), BUILD._plan('all'))
                with self.assertRaisesRegex(RuntimeError, 'Cannot build'):
                    BUILD._plan('windows' if target == 'linux' else 'linux')
        for host, architecture in [('darwin', 'x86_64'), ('linux', 'aarch64'),
                                   ('win32', 'ARM64'), ('unsupported', 'AMD64')]:
            with self.subTest(host=host, architecture=architecture), patch.object(BUILD, 'sys', SimpleNamespace(platform=host)), patch.object(BUILD.platform, 'machine', return_value=architecture):
                with self.assertRaisesRegex(RuntimeError, '[Uu]nsupported'):
                    BUILD._plan('all')
        with patch.object(BUILD, 'sys', SimpleNamespace(platform='darwin', executable='python3')), patch.object(BUILD.platform, 'machine', return_value='arm64'):
            self.assertEqual('macos', BUILD._plan('all')[0])
            self.assertTrue(str(BUILD._plan('macos')[2]).endswith('-macos-arm64.dmg'))

    def test_preview_and_existing_output_do_not_start_build(self):
        """Preview without mutations and preserve previous installer output.

        Existing intermediate output must also stop a normal build early.
        """
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            artifact = root / 'test.deb'
            for existing in (artifact, root / 'intermediate'):
                existing.touch()
                with patch.object(BUILD, '_plan', return_value=('linux', ['bash', 'helper'], artifact, [root / 'intermediate'])), patch.object(BUILD.subprocess, 'run') as run:
                    self.assertEqual(0, BUILD.main(['--all', '--dry-run']))
                    self.assertEqual(1, BUILD.main(['--linux']))
                    run.assert_not_called()
                existing.unlink()

    def test_propagates_failure_and_requires_new_nonempty_artifact(self):
        """Only report success after the helper creates a nonempty installer.

        Preserve its failure status and invoke it from the checkout root.
        """
        with tempfile.TemporaryDirectory() as temporary:
            artifact = Path(temporary) / 'test.msi'
            with patch.object(BUILD, '_plan', return_value=('windows', ['pwsh', 'helper'], artifact, [])), patch.object(BUILD.shutil, 'which', return_value='pwsh'):
                with patch.object(BUILD.subprocess, 'run', side_effect=subprocess.CalledProcessError(7, 'helper')):
                    self.assertEqual(7, BUILD.main(['--windows']))
                with patch.object(BUILD.subprocess, 'run'):
                    self.assertEqual(1, BUILD.main(['--windows']))
                with patch.object(BUILD.subprocess, 'run', side_effect=lambda *a, **k: artifact.write_bytes(b'installer')) as run:
                    self.assertEqual(0, BUILD.main(['--windows']))
                    self.assertEqual(BUILD.ROOT, run.call_args.kwargs['cwd'])


if __name__ == '__main__':
    unittest.main()
