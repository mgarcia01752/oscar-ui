# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
"""Verify removal handoff with mocked processes; never uninstall real software."""
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import Mock, patch

SPEC = importlib.util.spec_from_file_location('ubuntu_uninstall',
    Path(__file__).resolve().parents[1] / 'unix/ubuntu-uninstall.py')
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class UbuntuUninstallTest(unittest.TestCase):
    def test_success_and_authorization_cancel(self):
        for result in (0, 126):
            with self.subTest(result=result), patch.object(MODULE.os, 'geteuid', return_value=1000, create=True), \
                    patch.object(MODULE.os, 'kill', side_effect=ProcessLookupError), \
                    patch.object(MODULE.subprocess, 'Popen') as start, \
                    patch.object(MODULE.subprocess, 'run') as notice:
                start.side_effect = [Mock(wait=Mock(return_value=result)), Mock()]
                self.assertEqual(MODULE.uninstall(42, Path('/tmp/test.log')), result)
                command = start.call_args_list[0].args[0]
                self.assertEqual(command, ['/usr/bin/pkexec', '/usr/bin/apt-get', '-y',
                                 '-o', 'DPkg::Lock::Timeout=60', 'remove', 'oscar-ui'])
                self.assertIn('--info' if result == 0 else '--error', notice.call_args.args[0])
                self.assertEqual(start.call_count, 2)  # Never restart the removed application.

    def test_live_ui_prevents_removal(self):
        with patch.object(MODULE.os, 'geteuid', return_value=1000, create=True), \
                patch.object(MODULE.os, 'kill'), patch.object(MODULE.time, 'sleep'), \
                patch.object(MODULE.subprocess, 'Popen') as start:
            with self.assertRaisesRegex(ValueError, 'did not close'):
                MODULE.uninstall(42, Path('/tmp/test.log'))
            start.assert_not_called()

    def test_root_and_invalid_pid_prevent_removal(self):
        for uid, pid in ((0, 42), (1000, 1)):
            with patch.object(MODULE.os, 'geteuid', return_value=uid, create=True), \
                    patch.object(MODULE.subprocess, 'Popen') as start:
                with self.assertRaises(ValueError):
                    MODULE.uninstall(pid, Path('/tmp/test.log'))
                start.assert_not_called()
