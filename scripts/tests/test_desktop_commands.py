# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
"""Check desktop command ordering without invoking Maven or Qt."""
import importlib.util
from pathlib import Path
import os
import subprocess
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location('desktop', Path(__file__).resolve().parents[1] / 'build/desktop.py')
DESKTOP = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(DESKTOP)


class DesktopCommandsTest(unittest.TestCase):
    def setUp(self):
        """Provide a synthetic display for command-dispatch tests.

        No test connects to this display or launches a real application.
        """
        environment = patch.dict(os.environ, {'DISPLAY': ':fixture'})
        environment.start()
        self.addCleanup(environment.stop)

    def test_missing_display_stops_before_build_or_launch(self):
        """Reject interactive commands without a Linux display before building.

        Automated offscreen execution remains supported.
        """
        with patch.dict(os.environ, {}, clear=True), patch.object(DESKTOP.sys, 'platform', 'linux'):
            for arguments in (['run'], ['--build-then-run']):
                with patch.object(DESKTOP.sys, 'argv', ['desktop', *arguments]), patch.object(DESKTOP, 'build') as build, patch.object(DESKTOP, 'launch') as launch:
                    with self.assertRaisesRegex(RuntimeError, 'DISPLAY is not set'):
                        DESKTOP.main()
                    build.assert_not_called()
                    launch.assert_not_called()
            with patch.dict(os.environ, {'QT_QPA_PLATFORM': 'offscreen'}):
                DESKTOP._require_display()

    def test_build_then_run_orders_operations_and_stops_on_failure(self):
        """Launch only after successful build using the interactive launch mode.

        Failed builds must never open an older runnable JAR.
        """
        events = []
        with patch.object(DESKTOP.sys, 'argv', ['desktop', '--build-then-run']), patch.object(DESKTOP, 'verify_platform'), patch.object(DESKTOP, 'build', side_effect=lambda: events.append('build')), patch.object(DESKTOP, 'launch', side_effect=lambda smoke: events.append(('launch', smoke))):
            DESKTOP.main()
        self.assertEqual(['build', ('launch', False)], events)
        with patch.object(DESKTOP.sys, 'argv', ['desktop', '--build-then-run']), patch.object(DESKTOP, 'verify_platform'), patch.object(DESKTOP, 'build', side_effect=subprocess.CalledProcessError(1, 'maven')), patch.object(DESKTOP, 'launch') as launch:
            with self.assertRaises(subprocess.CalledProcessError):
                DESKTOP.main()
            launch.assert_not_called()

    def test_rejects_conflicting_or_missing_commands(self):
        """Reject ambiguous command selection before platform or build work.

        Existing explicit commands remain the normal single-operation interface.
        """
        for arguments in ([], ['run', '--build-then-run']):
            with patch.object(DESKTOP.sys, 'argv', ['desktop', *arguments]), patch.object(DESKTOP, 'verify_platform') as verify:
                with self.assertRaises(SystemExit) as error:
                    DESKTOP.main()
                self.assertEqual(2, error.exception.code)
                verify.assert_not_called()


if __name__ == '__main__':
    unittest.main()
