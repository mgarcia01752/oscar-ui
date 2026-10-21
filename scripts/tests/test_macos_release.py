# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
"""Verify macOS native extraction and launch isolation without macOS tools."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

SPEC = importlib.util.spec_from_file_location('macos_release', Path(__file__).resolve().parents[1] / 'build/macos-release.py')
MAC = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MAC)


class MacosReleaseTest(unittest.TestCase):
    def test_framework_executable_and_relative_link_survive_relocation(self):
        """Restore the native manifest links into a portable framework bundle."""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            archive = root / 'native.jar'
            binary = 'lib/QtJambi.framework/Versions/6/QtJambi'
            with zipfile.ZipFile(archive, 'w') as jar:
                jar.writestr(binary, b'native fixture')
                jar.writestr('META-INF/qtjambi-deployment.xml',
                             '<qtjambi-deploy><symlink name="lib/QtJambi.framework/QtJambi" '
                             f'target="{binary}"/></qtjambi-deploy>')
            destination = root / 'Frameworks'
            binaries = MAC.extract_frameworks(archive, destination)
            self.assertEqual(1, len(binaries))
            relocated = root / 'Moved'
            destination.rename(relocated)
            self.assertEqual(b'native fixture', (relocated / 'QtJambi.framework/QtJambi').read_bytes())

    def test_native_archive_cannot_escape_destination(self):
        """Reject traversal before writing an untrusted archive member."""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            archive = root / 'native.jar'
            with zipfile.ZipFile(archive, 'w') as jar:
                jar.writestr('lib/../../escaped', b'bad')
            with self.assertRaises(ValueError):
                MAC.extract_frameworks(archive, root / 'Frameworks')
            self.assertFalse((root / 'escaped').exists())

    def test_smoke_does_not_inherit_developer_runtime_overrides(self):
        """Require the packaged launcher, native plugin, and isolated environment."""
        with patch.dict(MAC.os.environ, {'QT_QPA_PLATFORM': 'offscreen', 'JAVA_HOME': '/host',
                                        'DYLD_FRAMEWORK_PATH': '/host/qt', '_JAVA_OPTIONS': '-bad'}), patch.object(MAC, 'run') as run:
            MAC.smoke(Path('/test/OSCAR-UI.app'))
        arguments = run.call_args.args[0]
        self.assertEqual(Path('/test/OSCAR-UI.app/Contents/MacOS/OSCAR-UI'), arguments[0])
        environment = run.call_args.kwargs['env']
        for key in ('QT_QPA_PLATFORM', 'JAVA_HOME', 'DYLD_FRAMEWORK_PATH', '_JAVA_OPTIONS'):
            self.assertNotIn(key, environment)


if __name__ == '__main__':
    unittest.main()
