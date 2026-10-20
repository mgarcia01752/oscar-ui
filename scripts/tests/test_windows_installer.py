# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
"""Production Windows family compatibility and release publication checks."""
import importlib.util
import json
from pathlib import Path
import shutil
import stat
import subprocess
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
        launcher = ('[Application]\r\napp.classpath=$APPDIR\\oscar-ui-' + version + '.jar\r\n'
                    '[JavaOptions]\r\njava-options=-Djpackage.app-version=' + version + '\r\n'
                    'java-options=-Djava.library.path=$APPDIR/..\r\n'
                    'java-options=-Dio.qt.pluginpath=$APPDIR/qt/plugins\r\n')
        (image / 'app/OSCAR-UI.cfg').write_bytes(launcher.encode('utf-8'))
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

    def test_readonly_launcher_can_prepare_previous_image(self):
        """Normalize real launcher attributes without changing patch identity."""
        repository = Path(__file__).resolve().parents[2]
        local = repository / '.tools/release-validation/pwsh/pwsh'
        powershell = shutil.which('pwsh') or (str(local) if local.is_file() else None)
        if not powershell:
            self.skipTest('PowerShell is unavailable')
        baseline, previous = self.baseline()
        image = self.image('1.0.1')
        launcher = image / 'OSCAR-UI.exe'
        original = launcher.read_bytes()
        launcher.chmod(stat.S_IREAD)
        # Cleanup must also work if the assertion fails on Windows.
        self.addCleanup(lambda: launcher.chmod(stat.S_IREAD | stat.S_IWRITE))
        harness = self.root / 'launcher-attributes.ps1'
        harness.write_text("""param($Source, $Image)
$ErrorActionPreference = 'Stop'
$Tokens = $null; $Errors = $null
$Ast = [System.Management.Automation.Language.Parser]::ParseFile($Source, [ref]$Tokens, [ref]$Errors)
if ($Errors.Count) { throw 'Invalid script syntax' }
$Function = $Ast.Find({ param($Node)
    $Node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and
    $Node.Name -eq 'Initialize-PatchLauncher'
}, $true)
Invoke-Expression $Function.Extent.Text
if (!(Get-Item (Join-Path $Image 'OSCAR-UI.exe')).IsReadOnly) {
    throw 'Fixture must start read-only'
}
Initialize-PatchLauncher $Image
if ((Get-Item (Join-Path $Image 'OSCAR-UI.exe')).IsReadOnly) {
    throw 'Launcher remains read-only'
}
""", encoding='utf-8')
        script = repository / 'scripts/windows/windows-msi.ps1'
        subprocess.run([powershell, '-NoProfile', '-File', str(harness), str(script), str(image)],
                       capture_output=True, text=True, encoding='utf-8', check=True)
        self.assertEqual(original, launcher.read_bytes())
        output = self.root / 'updated'
        metadata = INSTALLER.prepare(image, '1.0.1', output, previous)
        self.assertEqual(baseline['productCode'], metadata['productCode'])
        self.assertEqual(baseline['fingerprint'], metadata['fingerprint'])
        self.assertEqual('1.0.0', metadata['fromVersion'])
        self.assertEqual((previous / 'OSCAR-UI.exe').read_bytes(),
                         (output / 'previous-image/OSCAR-UI.exe').read_bytes())
        self.assertEqual(original, launcher.read_bytes())
        source = script.read_text(encoding='utf-8')
        self.assertLess(source.index('Initialize-PatchLauncher $Image'),
                        source.index('Invoke-Build python $Arguments'))

    def test_release_metadata_authentication_stays_on_github_api(self):
        """Exercise authenticated and anonymous requests without network access."""
        repository = Path(__file__).resolve().parents[2]
        local = repository / '.tools/release-validation/pwsh/pwsh'
        powershell = shutil.which('pwsh') or (str(local) if local.is_file() else None)
        if not powershell:
            self.skipTest('PowerShell is unavailable')
        harness = self.root / 'metadata.ps1'
        harness.write_text("""param($Source)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$Tokens = $null; $Errors = $null
$Ast = [System.Management.Automation.Language.Parser]::ParseFile($Source, [ref]$Tokens, [ref]$Errors)
if ($Errors.Count) { throw 'Invalid script syntax' }
$Function = $Ast.Find({ param($Node)
    $Node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and
    $Node.Name -eq 'Get-ReleaseMetadata'
}, $true)
Invoke-Expression $Function.Extent.Text
function Invoke-RestMethod($Uri, $Headers) {
    if ($Uri -ne "https://api.github.com/repos/mgarcia01752/oscar-ui/releases/$script:ExpectedPath") {
        throw 'Unexpected metadata host or path'
    }
    if ($Headers.Accept -ne 'application/vnd.github+json') { throw 'Missing API accept header' }
    if ($env:GH_TOKEN) {
        if ($Headers.Authorization -ne "Bearer $env:GH_TOKEN") { throw 'Missing authentication' }
    } elseif ($Headers.ContainsKey('Authorization')) { throw 'Unexpected anonymous authorization' }
    return 'metadata'
}
foreach ($Token in @('synthetic-test-token', '')) {
    $env:GH_TOKEN = $Token
    foreach ($Path in @('latest', 'tags/v1.2.3')) {
        $script:ExpectedPath = $Path
        if ((Get-ReleaseMetadata $Path) -ne 'metadata') { throw 'Lost response' }
    }
}
$Rejected = $false
try { Get-ReleaseMetadata 'https://example.invalid/asset' } catch { $Rejected = $true }
if (!$Rejected) { throw 'External metadata path was accepted' }
""", encoding='utf-8')
        script = repository / 'scripts/windows/windows-msi.ps1'
        subprocess.run([powershell, '-NoProfile', '-File', str(harness), str(script)],
                       capture_output=True, text=True, encoding='utf-8', check=True)
        source = script.read_text(encoding='utf-8')
        self.assertEqual(1, source.count('Invoke-RestMethod'))
        workflow = (repository / '.github/workflows/windows-release.yml').read_text(encoding='utf-8')
        build_step = workflow.split('- name: Build MSI and test application image', 1)[1].split('- name:', 1)[0]
        self.assertIn('GH_TOKEN: ${{ github.token }}', build_step)

    def test_torch_compatibility_is_scoped_to_build_copy(self):
        """Preserve installed tools and settings while configuring local Torch."""
        repository = Path(__file__).resolve().parents[2]
        local = repository / '.tools/release-validation/pwsh/pwsh'
        powershell = shutil.which('pwsh') or (str(local) if local.is_file() else None)
        if not powershell:
            self.skipTest('PowerShell is unavailable')
        wix = self.root / 'installed wix'
        wix.mkdir()
        (wix / 'torch.exe').write_bytes(b'synthetic executable')
        (wix / 'wix.dll').write_bytes(b'synthetic dependency')
        switch = 'Switch.System.DisableTempFileCollectionDirectoryFeature'
        harness = self.root / 'torch-config.ps1'
        harness.write_text("""param($Source, $Wix, $Destination)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$Tokens = $null; $Errors = $null
$Ast = [System.Management.Automation.Language.Parser]::ParseFile($Source, [ref]$Tokens, [ref]$Errors)
if ($Errors.Count) { throw 'Invalid script syntax' }
$Function = $Ast.Find({ param($Node)
    $Node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and
    $Node.Name -eq 'Initialize-PatchTorch'
}, $true)
Invoke-Expression $Function.Extent.Text
$Executable = Initialize-PatchTorch $Wix $Destination
if (!(Test-Path -LiteralPath $Executable)) { throw 'Missing local Torch' }
$Rejected = $false
try { Initialize-PatchTorch $Wix $Destination } catch { $Rejected = $true }
if (!$Rejected) { throw 'Existing output was overwritten' }
""", encoding='utf-8')
        for index, runtime in enumerate(['', '<runtime><AppContextSwitchOverrides value="Other=true;'
                                         + switch + '=false" /></runtime>']):
            config = '<configuration><startup><supportedRuntime version="v4.0" /></startup>' + runtime + '</configuration>'
            (wix / 'torch.exe.config').write_text(config, encoding='utf-8')
            output = self.root / f'local tools {index}'
            subprocess.run([powershell, '-NoProfile', '-File', str(harness),
                            str(repository / 'scripts/windows/windows-msi.ps1'), str(wix), str(output)],
                           capture_output=True, text=True, encoding='utf-8', check=True)
            self.assertEqual(config, (wix / 'torch.exe.config').read_text(encoding='utf-8'))
            tree = ET.parse(output / 'torch.exe.config')
            self.assertEqual('v4.0', tree.find('startup/supportedRuntime').get('version'))
            values = tree.find('runtime/AppContextSwitchOverrides').get('value').split(';')
            self.assertEqual(1, values.count(switch + '=true'))
            self.assertNotIn(switch + '=false', values)
            if runtime:
                self.assertIn('Other=true', values)
            for name in ('torch.exe', 'wix.dll'):
                self.assertEqual((wix / name).read_bytes(), (output / name).read_bytes())

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

    def test_shortcuts_use_current_user_registry_keypath(self):
        """Keep non-advertised shortcuts compatible with ICE38/ICE43/ICE57."""
        output = self.root / 'production'
        INSTALLER.prepare(self.image('1.0.0'), '1.0.0', output)
        namespace = {'w': INSTALLER.WIX.NAMESPACE}
        product = ET.parse(output / 'product.wxs').find('w:Product', namespace)
        self.assertEqual('perMachine', product.find('w:Package', namespace).get('InstallScope'))
        component = product.find(".//w:Component[@Id='ApplicationShortcuts']", namespace)
        keypaths = component.findall("w:RegistryValue[@KeyPath='yes']", namespace)
        self.assertEqual(1, len(keypaths))
        self.assertEqual('HKCU', keypaths[0].get('Root'))
        shortcuts = component.findall('w:Shortcut', namespace)
        self.assertEqual({'ApplicationMenu', 'DesktopFolder'}, {item.get('Directory') for item in shortcuts})
        self.assertTrue(all(item.get('Target') == '[INSTALLDIR]OSCAR-UI.exe' for item in shortcuts))
        cleanup = component.find("w:RemoveFolder[@Directory='ApplicationMenu']", namespace)
        self.assertEqual('uninstall', cleanup.get('On'))

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

    def test_real_launcher_option_change_requires_new_baseline(self):
        """Keep runtime options in the fingerprint after removing version metadata."""
        baseline, previous = self.baseline()
        image = self.image('1.0.1')
        config = image / 'app/OSCAR-UI.cfg'
        with config.open('ab') as stream:
            stream.write(b'java-options=-Xmx512m\r\n')
        target = INSTALLER.prepare(image, '1.0.1', self.root / 'updated', previous)
        self.assertNotEqual(baseline['productCode'], target['productCode'])
        self.assertIsNone(target['fromVersion'])
        content = config.read_text(encoding='utf-8')
        self.assertIn('java-options=-Xmx512m', content)
        self.assertIn('java-options=-Dio.qt.pluginpath=$APPDIR/qt/plugins', content)
        self.assertNotIn('jpackage.app-version', content)
        with zipfile.ZipFile(image / 'app/oscar-ui.jar') as jar:
            self.assertEqual(b'ui.version=1.0.1', jar.read('build-info.properties'))

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
