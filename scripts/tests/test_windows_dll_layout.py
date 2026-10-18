# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
"""Portable checks for the Windows release DLL relocation."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location(
    'windows_dll_layout', Path(__file__).resolve().parents[1] / 'build/windows-dll-layout.py')
LAYOUT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(LAYOUT)


class WindowsDllLayoutTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.image = Path(self.temporary.name) / 'OSCAR UI image'
        self.source = self.image / 'app/qt/lib'
        self.source.mkdir(parents=True)
        for name in (*LAYOUT.QT_LIBRARIES, 'opengl32sw.dll', 'd3dcompiler_47.dll'):
            (self.source / name).write_bytes(name.encode('ascii'))
        self.plugin = self.image / 'app/qt/plugins/platforms/qwindows.dll'
        self.plugin.parent.mkdir(parents=True)
        self.plugin.write_bytes(b'plugin')
        self.configuration = self.image / 'app/OSCAR-UI.cfg'
        self.original = b'[JavaOptions]\r\n' + LAYOUT.OLD_OPTION + b'\r\njava-options=-Dio.qt.pluginpath=$APPDIR/qt/plugins\r\n'
        self.configuration.write_bytes(self.original)

    def test_moves_all_libraries_and_preserves_plugins_and_configuration(self):
        LAYOUT.prepare(self.image)
        LAYOUT.verify(self.image)
        self.assertFalse(list(self.source.glob('*.dll')))
        for name in (*LAYOUT.QT_LIBRARIES, 'opengl32sw.dll', 'd3dcompiler_47.dll'):
            self.assertEqual((self.image / name).read_bytes(), name.encode('ascii'))
        self.assertEqual(self.configuration.read_bytes(), self.original.replace(LAYOUT.OLD_OPTION, LAYOUT.NEW_OPTION))
        self.assertEqual(self.plugin.read_bytes(), b'plugin')
        moved = self.image.with_name('Relocated installed image')
        self.image.rename(moved)
        LAYOUT.verify(moved)

    def test_destination_collision_does_not_modify_inputs(self):
        (self.image / 'Qt6Widgets.dll').write_bytes(b'existing')
        with self.assertRaisesRegex(RuntimeError, 'already exists'):
            LAYOUT.prepare(self.image)
        self.assertEqual(self.configuration.read_bytes(), self.original)
        self.assertEqual(len(list(self.source.glob('*.dll'))), 5)
        self.assertEqual((self.image / 'Qt6Widgets.dll').read_bytes(), b'existing')

    def test_unknown_configuration_is_rejected_before_moving(self):
        self.configuration.write_bytes(b'unexpected configuration')
        with self.assertRaisesRegex(RuntimeError, 'Unexpected launcher'):
            LAYOUT.prepare(self.image)
        self.assertEqual(len(list(self.source.glob('*.dll'))), 5)

    def test_missing_core_library_is_rejected_before_moving(self):
        (self.source / 'Qt6Core.dll').unlink()
        with self.assertRaisesRegex(RuntimeError, 'Missing staged'):
            LAYOUT.prepare(self.image)
        self.assertEqual(self.configuration.read_bytes(), self.original)
        self.assertTrue((self.source / 'Qt6Gui.dll').is_file())

    def test_verification_rejects_reintroduced_duplicate_or_wrong_path(self):
        LAYOUT.prepare(self.image)
        duplicate = self.source / 'Qt6Core.dll'
        duplicate.write_bytes(b'duplicate')
        with self.assertRaisesRegex(RuntimeError, 'Duplicate'):
            LAYOUT.verify(self.image)
        duplicate.unlink()
        self.configuration.write_bytes(self.original)
        with self.assertRaisesRegex(RuntimeError, 'Launcher must'):
            LAYOUT.verify(self.image)


if __name__ == '__main__':
    unittest.main()
