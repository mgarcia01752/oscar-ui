# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
"""Production Windows family compatibility and release publication checks."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET
import zipfile
from unittest.mock import patch


def load_module(name, filename):
    """Load a build helper without invoking its CLI."""
    spec = importlib.util.spec_from_file_location(name, Path(__file__).resolve().parents[1] / 'build' / filename)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


INSTALLER = load_module('windows_installer', 'windows-installer.py')
VERIFY = load_module('verify_release', 'verify-windows-release.py')


class WindowsInstallerTest(unittest.TestCase):
    """Verify production identity rules using synthetic app images."""

    def setUp(self):
        """Create independent app and resource fixtures without touching build output."""
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for directory in ['target', 'scripts/windows', 'packaging/windows']:
            (self.root / directory).mkdir(parents=True, exist_ok=True)
        for name in ['LICENSE', 'scripts/windows/windows-update.ps1', 'packaging/windows/install-information.txt',
                     'packaging/windows/dialog.bmp', 'packaging/windows/banner.bmp']:
            (self.root / name).write_text('synthetic content', encoding='utf-8')
        self.mock_root = patch.object(INSTALLER, 'ROOT', self.root)
        self.mock_root.start()
        self.addCleanup(self.mock_root.stop)

    def image(self, version, runtime=b'fixed runtime'):
        """Build a synthetic versioned image with an independently variable runtime."""
        image = self.root / version / 'image'
        (image / 'app').mkdir(parents=True)
        (image / 'runtime').mkdir()
        (image / 'OSCAR-UI.exe').write_bytes(version.encode())
        (image / 'runtime/release').write_bytes(runtime)
        (image / 'app/OSCAR-UI.cfg').write_text('app.classpath=$APPDIR\\oscar-ui-' + version + '.jar', encoding='utf-8')
        with zipfile.ZipFile(image / f'app/oscar-ui-{version}.jar', 'w') as jar:
            jar.writestr('build-info.properties', 'ui.version=' + version)
        return image

    def baseline(self):
        """Create a baseline and the compact retained inputs used by a later release."""
        image = self.image('1.0.0')
        output = self.root / 'baseline'
        metadata = INSTALLER.prepare(image, '1.0.0', output)
        (output / 'product.wixpdb').write_bytes(b'synthetic symbols')
        bundle = self.root / 'inputs.zip'
        INSTALLER.archive_inputs(image, output, bundle)
        previous = self.root / 'previous'
        with zipfile.ZipFile(bundle) as archive:
            self.assertEqual({'inputs.json', 'product.wixpdb', 'OSCAR-UI.exe', 'app/oscar-ui.jar'}, set(archive.namelist()))
            archive.extractall(previous)
        return metadata, previous

    def test_production_ui_owns_maintenance_property(self):
        """Avoid the duplicate ARPNOMODIFY symbol supplied by WixUI_InstallDir."""
        image = self.image('1.0.0')
        output = self.root / 'production'
        INSTALLER.prepare(image, '1.0.0', output)
        namespace = {'w': INSTALLER.WIX.NAMESPACE}
        product = ET.parse(output / 'product.wxs').find('w:Product', namespace)
        self.assertIsNotNone(product.find("w:UIRef[@Id='WixUI_InstallDir']", namespace))
        self.assertIsNone(product.find("w:Property[@Id='ARPNOMODIFY']", namespace))
        # The independent prototype has no UI extension to supply this property.
        prototype = self.root / 'prototype.wxs'
        INSTALLER.WIX._product(image, '1.0.0', prototype)
        standalone = ET.parse(prototype).find('w:Product', namespace)
        self.assertEqual('1', standalone.find("w:Property[@Id='ARPNOMODIFY']", namespace).get('Value'))

    def test_application_change_keeps_baseline_identity(self):
        """A new JAR and launcher are patchable with unchanged dependencies."""
        baseline, previous = self.baseline()
        image = self.image('1.0.1')
        output = self.root / 'updated'
        target = INSTALLER.prepare(image, '1.0.1', output, previous)
        self.assertEqual(baseline['productCode'], target['productCode'])
        self.assertEqual('1.0.0', target['fromVersion'])
        self.assertTrue((output / 'patch.wxs').exists())
        self.assertEqual((previous / 'app/oscar-ui.jar').read_bytes(),
                         (output / 'previous-image/app/oscar-ui.jar').read_bytes())
        self.assertNotEqual((image / 'app/oscar-ui.jar').read_bytes(),
                            (output / 'previous-image/app/oscar-ui.jar').read_bytes())

    def test_dependency_change_requires_new_baseline(self):
        """Reject an MSP when runtime bytes change, even across a patch version."""
        baseline, previous = self.baseline()
        output = self.root / 'updated'
        target = INSTALLER.prepare(self.image('1.0.1', b'new runtime'), '1.0.1', output, previous)
        self.assertNotEqual(baseline['productCode'], target['productCode'])
        self.assertIsNone(target['fromVersion'])
        self.assertFalse((output / 'patch.wxs').exists())

    def test_added_file_requires_new_baseline(self):
        """A changed installer file layout must not enter an existing patch family."""
        baseline, previous = self.baseline()
        image = self.image('1.0.1')
        (image / 'new.dll').write_bytes(b'new dependency')
        target = INSTALLER.prepare(image, '1.0.1', self.root / 'updated', previous)
        self.assertNotEqual(baseline['productCode'], target['productCode'])
        self.assertIsNone(target['fromVersion'])

    def test_branding_change_requires_new_baseline(self):
        """Installer UI changes must not silently join an existing patch family."""
        baseline, previous = self.baseline()
        (self.root / 'packaging/windows/dialog.bmp').write_bytes(b'updated branding')
        target = INSTALLER.prepare(self.image('1.0.1'), '1.0.1', self.root / 'updated', previous)
        self.assertNotEqual(baseline['productCode'], target['productCode'])
        self.assertIsNone(target['fromVersion'])

    def test_publication_requires_matching_patch_asset(self):
        """A missing MSP or mismatched digest prevents publication."""
        version = '1.0.1'
        assets = [{'name': name, 'size': 10, 'state': 'uploaded'} for name in
                  ['OSCAR-UI-1.0.1-windows-x64.msi', 'windows-patch-inputs.zip', 'windows-update.json']]
        manifest = {'version': version, 'layout': 1, 'fromVersion': None, 'patch': None}
        VERIFY.verify(version, assets, manifest)
        manifest['fromVersion'] = '1.0.0'
        manifest['patch'] = {'url': 'https://github.com/mgarcia01752/oscar-ui/releases/download/v1.0.1/OSCAR-UI-1.0.1-windows-x64.msp',
                             'size': 20, 'digest': 'sha256:' + 'a' * 64}
        with self.assertRaises(ValueError):
            VERIFY.verify(version, assets, manifest)
        asset = {'name': 'OSCAR-UI-1.0.1-windows-x64.msp', 'state': 'uploaded', 'size': 20,
                 'digest': manifest['patch']['digest']}
        assets.append(asset)
        VERIFY.verify(version, assets, manifest)
        asset['digest'] = 'sha256:' + 'b' * 64
        with self.assertRaises(ValueError):
            VERIFY.verify(version, assets, manifest)
