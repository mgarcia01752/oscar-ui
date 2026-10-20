# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
"""Portable tests for Windows patch input authoring; no MSI installation."""
import importlib.util
import json
import shutil
import subprocess
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET
import zipfile

SPEC = importlib.util.spec_from_file_location(
    'patch_prototype', Path(__file__).resolve().parents[1] / 'build/windows-patch-prototype.py')
PATCH = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PATCH)
NS = {'w': PATCH.NAMESPACE}


class WindowsPatchPrototypeTest(unittest.TestCase):
    """Exercise stable identities, payload isolation and verification failures."""

    def setUp(self):
        """Create synthetic executable, runtime, launcher and JAR inputs."""
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.image = self.root / 'source image'
        (self.image / 'app').mkdir(parents=True)
        (self.image / 'runtime').mkdir()
        (self.image / 'runtime/release').write_bytes(b'pinned runtime')
        (self.image / 'OSCAR-UI.exe').write_bytes(b'synthetic launcher')
        (self.image / 'app/OSCAR-UI.cfg').write_text(
            '[Application]\napp.classpath=$APPDIR\\oscar-ui-0.4.3.jar\n', encoding='utf-8')
        with zipfile.ZipFile(self.image / 'app/oscar-ui-0.4.3.jar', 'w') as archive:
            archive.writestr('build-info.properties', 'ui.version=0.4.3\noscar.version=3.3.0\n')
            archive.writestr('application.class', b'synthetic unchanged application code')
        self.output = self.root / 'prototype'

    def test_msiexec_package_arguments_use_backslashes_and_quotes(self):
        """Execute the actual PowerShell helper without running installer actions."""
        repository = Path(__file__).resolve().parents[2]
        local_powershell = repository / '.tools/release-validation/pwsh/pwsh'
        powershell = shutil.which('pwsh') or (str(local_powershell) if local_powershell.is_file() else None)
        if not powershell:
            self.skipTest('PowerShell is unavailable')
        package = self.root / 'package with spaces.msi'
        package.write_bytes(b'synthetic package')
        script = repository / 'scripts/windows/test-windows-patch.ps1'
        harness = self.root / 'test-path.ps1'
        harness.write_text("""param($Source, $Package)
$ErrorActionPreference = 'Stop'
$Tokens = $null; $Errors = $null
$Ast = [System.Management.Automation.Language.Parser]::ParseFile($Source, [ref]$Tokens, [ref]$Errors)
if ($Errors.Count) { throw 'Invalid script syntax' }
$Function = $Ast.Find({ param($Node)
    $Node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and
    $Node.Name -eq 'Get-MsiPackageArgument'
}, $true)
Invoke-Expression $Function.Extent.Text
Get-MsiPackageArgument $Package
""", encoding='utf-8')
        result = subprocess.run([powershell, '-NoProfile', '-File', str(harness), str(script),
                                 str(package)], capture_output=True, text=True, encoding='utf-8', check=True)
        self.assertEqual('"' + str(package.resolve()).replace('/', '\\') + '"', result.stdout.strip())
        package.unlink()
        missing = subprocess.run([powershell, '-NoProfile', '-File', str(harness), str(script),
                                  str(package)], capture_output=True, text=True, encoding='utf-8')
        self.assertNotEqual(0, missing.returncode)

    def test_only_application_jar_changes_and_source_is_preserved(self):
        """Ensure the paired fixtures differ only in their application version."""
        original = PATCH._hashes(self.image)
        manifest = PATCH.prepare(self.image, self.output)
        self.assertEqual(['app/oscar-ui.jar'], manifest['changedFiles'])
        self.assertEqual(original, PATCH._hashes(self.image))
        for stage in ('baseline', 'target'):
            PATCH.verify(self.output / stage / 'image', self.output / 'evidence.json', stage)
            config = (self.output / stage / 'image/app/OSCAR-UI.cfg').read_text(encoding='utf-8')
            self.assertIn('oscar-ui.jar', config)
            self.assertNotIn('oscar-ui-0.4.3.jar', config)

    def test_product_and_component_identities_are_stable(self):
        """Require consistent path keys, GUIDs and product identity in both MSIs."""
        PATCH.prepare(self.image, self.output)
        baseline, target = [ET.parse(self.output / stage / 'product.wxs') for stage in ('baseline', 'target')]
        for element in ('Component', 'Directory', 'ComponentRef'):
            self.assertEqual([item.attrib for item in baseline.findall('.//w:' + element, NS)],
                             [item.attrib for item in target.findall('.//w:' + element, NS)])
        before = baseline.find('w:Product', NS)
        after = target.find('w:Product', NS)
        self.assertEqual(before.get('Id'), after.get('Id'))
        self.assertNotEqual(before.get('Version'), after.get('Version'))
        patch = ET.parse(self.output / 'patch.wxs')
        self.assertEqual([PATCH._identifier('C', PATCH.APP_JAR)],
                         [item.get('Id') for item in patch.findall('.//w:ComponentRef', NS)])

    def test_changed_dependency_is_rejected(self):
        """A runtime mutation must fail installed payload verification."""
        PATCH.prepare(self.image, self.output)
        installed = self.output / 'target/image'
        (installed / 'runtime/release').write_bytes(b'changed')
        with self.assertRaisesRegex(ValueError, 'runtime/release'):
            PATCH.verify(installed, self.output / 'evidence.json', 'target')

    def test_wrong_version_is_rejected_even_with_updated_hash(self):
        """Check the About resource independently of the expected hash table."""
        PATCH.prepare(self.image, self.output)
        evidence = self.output / 'evidence.json'
        manifest = json.loads(evidence.read_text(encoding='utf-8'))
        manifest['hashes']['target'] = manifest['hashes']['baseline']
        evidence.write_text(json.dumps(manifest), encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'About version'):
            PATCH.verify(self.output / 'baseline/image', evidence, 'target')

    def test_existing_output_is_not_overwritten(self):
        """Preserve previous evidence on repeated invocation."""
        PATCH.prepare(self.image, self.output)
        with self.assertRaises(FileExistsError):
            PATCH.prepare(self.image, self.output)

    def test_nested_output_is_rejected_before_mutation(self):
        """Prevent recursive copying when output is inside the app image."""
        with self.assertRaisesRegex(ValueError, 'separate'):
            PATCH.prepare(self.image, self.image / 'output')
        self.assertFalse((self.image / 'output').exists())

    def test_invalid_launcher_is_rejected_before_output_creation(self):
        """Fail clearly if future packaging changes the expected launcher format."""
        (self.image / 'app/OSCAR-UI.cfg').write_text('wrong.jar', encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'Launcher'):
            PATCH.prepare(self.image, self.output)
        self.assertFalse(self.output.exists())
