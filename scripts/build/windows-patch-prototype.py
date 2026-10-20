#!/usr/bin/env python3
# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
"""Prepare isolated WiX patch fixtures from a real Windows application image."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import uuid
import xml.etree.ElementTree as ET
import zipfile

NAMESPACE = 'http://schemas.microsoft.com/wix/2006/wi'
PRODUCT_CODE = 'C86DCBBA-7394-43F0-BF23-376EBD499FE6'
UPGRADE_CODE = '2F91C801-46FA-4D79-8650-2AFD6D83BD75'
COMPONENT_NAMESPACE = uuid.UUID('662d0219-fbef-4d83-a3af-4e93bc426cf1')
APP_JAR = 'app/oscar-ui.jar'
ET.register_namespace('', NAMESPACE)


def prepare(image, output):
    """Copy a complete image into fresh synthetic baseline/target fixtures.

    Never modify the input image or overwrite output. Return the evidence
    manifest; invalid launcher/JAR layout raises ValueError before copying.
    """
    image, output = Path(image).resolve(), Path(output).resolve()
    jars = list((image / 'app').glob('oscar-ui-*.jar'))
    if len(jars) != 1 or not (image / 'OSCAR-UI.exe').is_file():
        raise ValueError('Expected one versioned application JAR and the Windows launcher')
    if output == image or image in output.parents or output in image.parents:
        raise ValueError('Output must be separate from the input image')
    configuration = image / 'app/OSCAR-UI.cfg'
    launcher_text = configuration.read_text(encoding='utf-8')
    if jars[0].name not in launcher_text:
        raise ValueError('Launcher does not reference the application JAR')
    output.mkdir(parents=True, exist_ok=False)
    snapshots = {}
    for label, version in [('baseline', '0.0.1'), ('target', '0.0.2')]:
        destination = output / label / 'image'
        shutil.copytree(image, destination)
        copied_jar = destination / 'app' / jars[0].name
        stable_jar = destination / APP_JAR
        copied_jar.rename(stable_jar)
        (destination / 'app/OSCAR-UI.cfg').write_text(
            launcher_text.replace(jars[0].name, 'oscar-ui.jar'), encoding='utf-8')
        _set_version(stable_jar, version)
        snapshots[label] = _hashes(destination)
        _product(destination, version, output / label / 'product.wxs')
    changes = [name for name in snapshots['baseline']
               if snapshots['baseline'][name] != snapshots['target'][name]]
    if changes != [APP_JAR]:
        raise ValueError(f'Only the application JAR may change: {changes}')
    wix = _element('Wix')
    patch = _element('Patch', wix, AllowRemoval='yes', Manufacturer='OSCAR-UI contributors',
                     DisplayName='OSCAR-UI PATCH PROTOTYPE ONLY', Description='Synthetic application update',
                     Classification='Update')
    media = _element('Media', patch, Id='5000', Cabinet='patch.cab')
    _element('PatchBaseline', media, Id='Baseline')
    family = _element('PatchFamily', patch, Id='Prototype', Version='0.0.2.0', Supersede='yes')
    _element('ComponentRef', family, Id=_identifier('C', APP_JAR))
    _write_xml(wix, output / 'patch.wxs')
    manifest = {'productCode': PRODUCT_CODE, 'versions': {'baseline': '0.0.1', 'target': '0.0.2'},
                'hashes': snapshots, 'changedFiles': changes,
                'scope': 'Synthetic prototype only; not compatible with release installations'}
    (output / 'evidence.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    return manifest


def verify(image, evidence, stage):
    """Verify every installed payload byte and the version resource used by About.

    Raise ValueError on missing, additional or changed files, or a wrong version.
    This does not prove visual UI behavior or Windows Installer registration.
    """
    manifest = json.loads(Path(evidence).read_text(encoding='utf-8'))
    actual = _hashes(Path(image))
    expected = manifest['hashes'][stage]
    if actual != expected:
        changed = sorted(name for name in actual.keys() | expected.keys()
                         if actual.get(name) != expected.get(name))
        raise ValueError(f'Installed payload differs: {changed}')
    with zipfile.ZipFile(Path(image) / APP_JAR) as archive:
        properties = archive.read('build-info.properties').decode('utf-8')
    if f"ui.version={manifest['versions'][stage]}" not in properties.splitlines():
        raise ValueError('About version does not match installed stage')


def main():
    """Prepare fixtures or verify installed payloads; report failures without cleanup."""
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    build = commands.add_parser('prepare')
    build.add_argument('--image', required=True, type=Path)
    build.add_argument('--output', required=True, type=Path)
    check = commands.add_parser('verify')
    check.add_argument('--image', required=True, type=Path)
    check.add_argument('--evidence', required=True, type=Path)
    check.add_argument('--stage', choices=['baseline', 'target'], required=True)
    args = parser.parse_args()
    if args.command == 'prepare':
        prepare(args.image, args.output)
    else:
        verify(args.image, args.evidence, args.stage)
    print(f'[Windows patch prototype] {args.command} passed', flush=True)


def _element(name, parent=None, **attributes):
    tag = '{' + NAMESPACE + '}' + name
    return ET.Element(tag, attributes) if parent is None else ET.SubElement(parent, tag, attributes)


def _identifier(prefix, path):
    return prefix + hashlib.sha256(path.encode('utf-8')).hexdigest()[:32]


def _hashes(directory):
    hashes = {}
    for path in sorted(directory.rglob('*')):
        if path.is_file():
            with path.open('rb') as stream:
                digest = hashlib.sha256()
                for chunk in iter(lambda: stream.read(1024 * 1024), b''):
                    digest.update(chunk)
                hashes[path.relative_to(directory).as_posix()] = digest.hexdigest()
    return hashes


def _set_version(jar, version):
    replacement = jar.with_suffix('.new')
    with zipfile.ZipFile(jar) as source, zipfile.ZipFile(replacement, 'w') as target:
        if 'build-info.properties' not in source.namelist():
            raise ValueError('Missing application version resource')
        for entry in source.infolist():
            contents = source.read(entry.filename)
            if entry.filename == 'build-info.properties':
                text, count = re.subn(r'^ui.version=.*$', f'ui.version={version}',
                                      contents.decode('utf-8'), flags=re.M)
                if count != 1:
                    raise ValueError('Expected exactly one application version')
                contents = text.encode('utf-8')
            target.writestr(entry, contents)
    replacement.replace(jar)


def _product(image, version, destination):
    wix = _element('Wix')
    product = _element('Product', wix, Id=PRODUCT_CODE, UpgradeCode=UPGRADE_CODE,
                       Name='OSCAR-UI Patch Prototype', Language='1033', Version=version,
                       Manufacturer='OSCAR-UI contributors')
    _element('Package', product, Id='*', InstallerVersion='500', Compressed='yes',
             InstallScope='perMachine', Platform='x64')
    _element('Media', product, Id='1', Cabinet='payload.cab', EmbedCab='yes')
    _element('Property', product, Id='ARPNOMODIFY', Value='1')
    root = _element('Directory', product, Id='TARGETDIR', Name='SourceDir')
    program_files = _element('Directory', root, Id='ProgramFiles64Folder')
    install = _element('Directory', program_files, Id='INSTALLDIR', Name='OSCAR-UI Patch Prototype')
    directories = {'.': install}
    feature = _element('Feature', product, Id='Application', Title='Prototype', Level='1')
    for path in sorted(image.rglob('*')):
        relative = path.relative_to(image).as_posix()
        parent = directories[path.parent.relative_to(image).as_posix()]
        if path.is_dir():
            directories[relative] = _element('Directory', parent, Id=_identifier('D', relative), Name=path.name)
        else:
            component_id = _identifier('C', relative)
            component = _element('Component', parent, Id=component_id, Win64='yes',
                                 Guid=str(uuid.uuid5(COMPONENT_NAMESPACE, relative)))
            _element('File', component, Id=_identifier('F', relative), Name=path.name,
                     Source=str(path), KeyPath='yes')
            _element('ComponentRef', feature, Id=component_id)
    _write_xml(wix, destination)


def _write_xml(element, destination):
    ET.indent(element)
    ET.ElementTree(element).write(destination, encoding='utf-8', xml_declaration=True)


if __name__ == '__main__':
    main()
