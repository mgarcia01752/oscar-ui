# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
import hashlib
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
import sys
import os
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location('ubuntu_update', Path(__file__).resolve().parents[1] / 'unix/ubuntu-update.py')
UPDATER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(UPDATER)


@unittest.skipUnless(sys.platform.startswith("linux"), "Ubuntu installer helper")
class UbuntuUpdateTest(unittest.TestCase):
    def test_metadata_digest_and_version_gate(self):
        with tempfile.TemporaryDirectory() as directory:
            package = Path(directory) / 'package.deb'
            package.write_bytes(b'synthetic')
            digest = hashlib.sha256(package.read_bytes()).hexdigest()
            for fields, success in [(['oscar-ui', 'amd64', '0.4.0-1', 'install ok installed\n0.3.0'], True),
                                    (['wrong', 'amd64', '0.4.0', 'install ok installed\n0.3.0'], False),
                                    (['oscar-ui', 'arm64', '0.4.0', 'install ok installed\n0.3.0'], False),
                                    (['oscar-ui', 'amd64', '0.5.0', 'install ok installed\n0.3.0'], False)]:
                with patch.object(UPDATER.subprocess, 'check_output', side_effect=fields), \
                     patch.object(UPDATER.subprocess, 'run', return_value=subprocess.CompletedProcess([], 0)):
                    if success:
                        UPDATER.validate_package(package, '0.4.0', digest)
                    else:
                        with self.assertRaises(ValueError):
                            UPDATER.validate_package(package, '0.4.0', digest)
            with self.assertRaises(ValueError):
                UPDATER.validate_package(package, '0.4.0', '0' * 64)
            with patch.object(UPDATER.subprocess, 'check_output', side_effect=[
                    'oscar-ui', 'amd64', '0.4.0', 'install ok installed\n0.4.0']), \
                 patch.object(UPDATER.subprocess, 'run', return_value=subprocess.CompletedProcess([], 1)):
                with self.assertRaises(ValueError):
                    UPDATER.validate_package(package, '0.4.0', digest)

    def test_authentication_cancellation_does_not_restart_or_remove_download(self):
        with tempfile.TemporaryDirectory() as directory:
            package = Path(directory) / 'update.deb'
            package.write_bytes(b'synthetic')
            with patch.object(UPDATER.os, 'geteuid', return_value=1000), \
                 patch.object(UPDATER.os, 'kill', side_effect=ProcessLookupError), \
                 patch.object(UPDATER.subprocess, 'run', return_value=subprocess.CompletedProcess([], 126)) as run, \
                 patch.object(UPDATER.subprocess, 'Popen') as launch:
                launch.return_value.wait.return_value = 126
                UPDATER.run_after_exit(1234, package, '0.4.0', '0' * 64)
                self.assertEqual(launch.call_count, 2)
                self.assertTrue(package.exists())
                self.assertEqual(launch.call_args_list[0].args[0][0], '/usr/bin/pkexec')

    def test_wait_timeout_never_installs(self):
        with patch.object(UPDATER.os, 'geteuid', return_value=1000), \
             patch.object(UPDATER.os, 'kill'), patch.object(UPDATER.time, 'sleep'), \
             patch.object(UPDATER.subprocess, 'run') as run:
            with self.assertRaises(ValueError):
                UPDATER.run_after_exit(1234, Path('/unused'), '0.4.0', '0' * 64)
            run.assert_not_called()

    def test_install_validates_private_copy_before_apt(self):
        with tempfile.TemporaryDirectory() as directory:
            package = Path(directory) / 'update.deb'
            package.write_bytes(b'synthetic')
            checked = []
            def validate(private, version, digest):
                self.assertNotEqual(private, package)
                self.assertEqual(private.read_bytes(), package.read_bytes())
                self.assertEqual(private.parent.stat().st_mode & 0o777, 0o700)
                checked.append(private)
            with patch.object(UPDATER.os, 'geteuid', return_value=0), \
                 patch.dict(os.environ, {'PKEXEC_UID': str(os.getuid())}), \
                 patch.object(UPDATER, 'validate_package', side_effect=validate), \
                 patch.object(UPDATER.subprocess, 'run') as run:
                UPDATER.install(package, '0.4.0', '0' * 64)
                self.assertEqual(len(checked), 1)
                command = run.call_args.args[0]
                self.assertEqual(command[0], '/usr/bin/apt-get')
                self.assertIn('--no-remove', command)
                self.assertIn('DPkg::Lock::Timeout=60', command)
                self.assertEqual(command[-1], str(checked[0]))
                self.assertFalse(checked[0].exists())

    def test_invalid_package_and_symlink_never_invoke_apt(self):
        with tempfile.TemporaryDirectory() as directory:
            package = Path(directory) / 'update.deb'
            package.write_bytes(b'invalid')
            link = Path(directory) / 'link.deb'
            link.symlink_to(package)
            with patch.object(UPDATER.os, 'geteuid', return_value=0), \
                 patch.dict(os.environ, {'PKEXEC_UID': str(os.getuid())}), \
                 patch.object(UPDATER.subprocess, 'run') as run:
                for source in (package, link):
                    with self.assertRaises((ValueError, OSError)):
                        UPDATER.install(source, '0.4.0', '0' * 64)
                run.assert_not_called()
