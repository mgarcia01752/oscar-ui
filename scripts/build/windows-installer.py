#!/usr/bin/env python3
# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
"""Author the production Windows baseline and exact-previous-release patches."""
import argparse
import hashlib
import importlib.util
import json
import re
from pathlib import Path
import shutil
import uuid
import zipfile

SPEC = importlib.util.spec_from_file_location('prototype', Path(__file__).with_name('windows-patch-prototype.py'))
WIX = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(WIX)
ROOT = Path(__file__).resolve().parents[2]
FAMILY = uuid.UUID('8a715142-858b-426c-abdf-5b2ba821bb03')
UPGRADE = '80186748-531f-4a39-a6da-f5c81a1f0730'
MUTABLE = ['OSCAR-UI.exe', 'app/oscar-ui.jar']
BASELINE = 'app/windows-baseline.json'


def prepare(image, version, output, previous=None):
    """Normalize a fresh image and author its MSI and optional MSP inputs.

    Previous is an extracted, verified release input archive. Dependency or layout
    differences establish a new baseline. Output must not exist. Return metadata.
    """
    if not version or not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+', version):
        raise ValueError('Expected a stable three-part MSI version')
    parts = tuple(map(int, version.split('.')))
    if parts[0] > 255 or parts[1] > 255 or parts[2] > 65535:
        raise ValueError('Version exceeds Windows Installer limits')
    image, output = Path(image).resolve(), Path(output).resolve()
    output.mkdir(parents=True, exist_ok=False)
    jars = list((image / 'app').glob('oscar-ui-*.jar'))
    if len(jars) != 1:
        raise ValueError('Expected one versioned application JAR')
    config = image / 'app/OSCAR-UI.cfg'
    text = config.read_text(encoding='utf-8')
    if jars[0].name not in text:
        raise ValueError('Launcher does not reference application JAR')
    # jpackage injects the release number here, but OSCAR-UI reads its version
    # from the JAR. This redundant property must not change dependency identity.
    text = ''.join(line for line in text.splitlines(keepends=True)
                   if not line.startswith('java-options=-Djpackage.app-version='))
    config.write_text(text.replace(jars[0].name, 'oscar-ui.jar'), encoding='utf-8')
    jars[0].rename(image / MUTABLE[1])
    # jpackage build metadata is not an installed runtime dependency.
    for metadata in image.rglob('.jpackage.xml'):
        metadata.unlink()
    shutil.copy2(ROOT / 'scripts/windows/windows-update.ps1', image / 'app/windows-update.ps1')
    hashes = WIX._hashes(image)
    dependencies = {name: digest for name, digest in hashes.items() if name not in MUTABLE}
    authoring_inputs = [Path(__file__), Path(WIX.__file__), ROOT / 'LICENSE',
                        ROOT / 'packaging/windows/install-information.txt',
                        ROOT / 'packaging/windows/dialog.bmp', ROOT / 'packaging/windows/banner.bmp']
    authoring = ''.join(hashlib.sha256(path.read_bytes()).hexdigest() for path in authoring_inputs)
    fingerprint = hashlib.sha256((json.dumps(dependencies, sort_keys=True) + authoring).encode()).hexdigest()
    prior = json.loads((previous / 'inputs.json').read_text(encoding='utf-8')) if previous else None
    compatible = (prior is not None and prior.get('layout') == 1 and prior.get('fingerprint') == fingerprint
                  and prior.get('files') == sorted(hashes)
                  and tuple(map(int, prior['version'].split('.'))) < parts)
    product_code = prior['productCode'] if compatible else str(uuid.uuid5(FAMILY, version + fingerprint))
    baseline_version = prior['baselineVersion'] if compatible else version
    baseline = {'layout': 1, 'productCode': product_code, 'baselineVersion': baseline_version,
                'fingerprint': fingerprint, 'dependencies': dependencies}
    (image / BASELINE).write_text(json.dumps(baseline, sort_keys=True, indent=2) + '\n', encoding='utf-8')
    metadata = {'layout': 1, 'version': version, 'productCode': product_code,
                'baselineVersion': baseline_version, 'fingerprint': fingerprint, 'files': sorted(hashes),
                'fromVersion': prior['version'] if compatible else None}
    (output / 'inputs.json').write_text(json.dumps(metadata, indent=2), encoding='utf-8')
    _product(image, version, product_code, output / 'product.wxs')
    if compatible:
        old_image = output / 'previous-image'
        shutil.copytree(image, old_image)
        for name in MUTABLE:
            shutil.copy2(previous / name, old_image / name)
        shutil.copy2(previous / 'product.wixpdb', output / 'previous.wixpdb')
        _patch(version, output / 'patch.wxs')
    (output / 'expected.json').write_text(json.dumps(WIX._hashes(image)), encoding='utf-8')
    return metadata


def archive_inputs(image, output, archive):
    """Retain only symbols and mutable application files, not duplicate runtimes."""
    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as bundle:
        for name in ['inputs.json', 'product.wixpdb']:
            bundle.write(output / name, name)
        for name in MUTABLE:
            bundle.write(image / name, name)


def main():
    """Dispatch production authoring, input retention and installed hash checks."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['prepare', 'archive', 'verify'])
    parser.add_argument('--image', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--version')
    parser.add_argument('--previous', type=Path)
    parser.add_argument('--archive', type=Path)
    args = parser.parse_args()
    if args.command == 'prepare':
        prepare(args.image, args.version, args.output, args.previous)
    elif args.command == 'archive':
        archive_inputs(args.image, args.output, args.archive)
    elif WIX._hashes(args.image) != json.loads((args.output / 'expected.json').read_text(encoding='utf-8')):
        raise ValueError('Installed application files differ from the release payload')
    print('[Windows installer] ' + args.command + ' passed', flush=True)


def _product(image, version, product_code, destination):
    # Reuse the tested stable path/component authoring, then supply production UI.
    WIX._product(image, version, destination)
    import xml.etree.ElementTree as ET
    tree = ET.parse(destination)
    namespace = {'w': WIX.NAMESPACE}
    product = tree.find('w:Product', namespace)
    product.set('Id', product_code)
    product.set('UpgradeCode', UPGRADE)
    product.set('Name', 'OSCAR-UI')
    install = product.find(".//w:Directory[@Id='INSTALLDIR']", namespace)
    install.set('Name', 'OSCAR-UI')
    for component in product.findall('.//w:Component', namespace):
        component.set('Guid', str(uuid.uuid5(uuid.UUID(product_code), component.get('Id'))))
    for file in product.findall('.//w:File', namespace):
        relative = Path(file.get('Source')).relative_to(image).as_posix()
        file.set('Source', '!(bindpath.payload)\\' + relative.replace('/', '\\'))
    feature = product.find('w:Feature', namespace)
    feature.set('Title', 'OSCAR-UI')
    element = WIX._element
    element('Property', product, Id='WIXUI_INSTALLDIR', Value='INSTALLDIR')
    # WixUI_InstallDir owns ARPNOMODIFY; the standalone prototype defines its
    # own copy. Keeping both causes WiX linker errors LGHT0091/LGHT0092.
    for property_element in product.findall("w:Property[@Id='ARPNOMODIFY']", namespace):
        product.remove(property_element)
    element('UIRef', product, Id='WixUI_InstallDir')
    element('WixVariable', product, Id='WixUILicenseRtf', Value=str(ROOT / 'target/windows-license.rtf'))
    for identifier, filename in [('WixUIDialogBmp', 'dialog.bmp'), ('WixUIBannerBmp', 'banner.bmp')]:
        element('WixVariable', product, Id=identifier, Value=str(ROOT / 'packaging/windows' / filename))
    # The native executable contains the same application icon.
    element('Icon', product, Id='ApplicationIcon', SourceFile='!(bindpath.payload)\\OSCAR-UI.exe')
    element('Property', product, Id='ARPPRODUCTICON', Value='ApplicationIcon')
    root = product.find("w:Directory[@Id='TARGETDIR']", namespace)
    programs = element('Directory', root, Id='ProgramMenuFolder')
    menu = element('Directory', programs, Id='ApplicationMenu', Name='OSCAR-UI')
    element('Directory', root, Id='DesktopFolder')
    shortcuts = element('Component', menu, Id='ApplicationShortcuts', Win64='yes',
                        Guid=str(uuid.uuid5(uuid.UUID(product_code), 'shortcuts')))
    for identifier, directory in [('MenuShortcut', 'ApplicationMenu'), ('DesktopShortcut', 'DesktopFolder')]:
        element('Shortcut', shortcuts, Id=identifier, Directory=directory, Name='OSCAR-UI',
                Target='[INSTALLDIR]OSCAR-UI.exe', WorkingDirectory='INSTALLDIR', Icon='ApplicationIcon')
    element('RemoveFolder', shortcuts, Id='RemoveApplicationMenu', Directory='ApplicationMenu', On='uninstall')
    # Non-advertised shell shortcuts require an HKCU registry key path
    # (ICE38/ICE43/ICE57), even when the MSI installs per-machine.
    element('RegistryValue', shortcuts, Root='HKCU', Key='Software\\OSCAR-UI', Name='Installed',
            Type='integer', Value='1', KeyPath='yes')
    element('ComponentRef', feature, Id='ApplicationShortcuts')
    upgrade = element('Upgrade', product, Id=UPGRADE)
    element('UpgradeVersion', upgrade, Minimum='0.0.0', IncludeMinimum='yes', OnlyDetect='yes', Property='OTHER_OSCAR_UI')
    condition = element('Condition', product, Message='Uninstall the previous OSCAR-UI installation before installing this baseline.')
    condition.text = 'Installed OR NOT OTHER_OSCAR_UI'
    WIX._write_xml(tree.getroot(), destination)
    license_text = (ROOT / 'packaging/windows/install-information.txt').read_text(encoding='utf-8') + '\n' + (ROOT / 'LICENSE').read_text(encoding='utf-8')
    escaped = license_text.replace('\\', '\\\\').replace('{', '\\{').replace('}', '\\}').replace('\n', '\\par\n')
    (ROOT / 'target/windows-license.rtf').write_text('{\\rtf1\\ansi\n' + escaped + '}', encoding='utf-8')


def _patch(version, destination):
    element = WIX._element
    wix = element('Wix')
    patch = element('Patch', wix, AllowRemoval='yes', Manufacturer='OSCAR-UI contributors',
                    DisplayName='OSCAR-UI ' + version, Description='OSCAR-UI application update', Classification='Update')
    media = element('Media', patch, Id='5000', Cabinet='patch.cab')
    element('PatchBaseline', media, Id='Previous')
    family = element('PatchFamily', patch, Id='OscarUiApplication', Version=version + '.0', Supersede='yes')
    for name in MUTABLE:
        element('ComponentRef', family, Id=WIX._identifier('C', name))
    element('PropertyRef', family, Id='ProductVersion')
    # The native launcher icon is versioned together with the executable.
    element('IconRef', family, Id='ApplicationIcon')
    WIX._write_xml(wix, destination)


if __name__ == '__main__':
    main()
