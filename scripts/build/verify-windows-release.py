#!/usr/bin/env python3
# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
"""Reject incomplete Windows patch release metadata before publishing a draft."""
import argparse
import json
import re
from pathlib import Path


def verify(version, assets, manifest):
    """Validate required Windows assets and optional MSP identity against a release.

    Raise ValueError for missing, duplicate, empty or inconsistent release assets.
    Assets may come from gh release view or the REST release endpoint.
    """
    if manifest.get('version') != version or manifest.get('layout') != 1:
        raise ValueError('Windows update manifest version/layout mismatch')
    for name in [f'OSCAR-UI-{version}-windows-x64.msi', 'windows-patch-inputs.zip', 'windows-update.json']:
        matches = [asset for asset in assets if asset['name'] == name]
        if len(matches) != 1 or matches[0].get('size', 0) <= 0 or matches[0].get('state') != 'uploaded':
            raise ValueError(f'Missing/invalid Windows asset: {name}')
    patches = [asset for asset in assets if asset['name'].endswith('.msp')]
    patch = manifest.get('patch')
    if not patch:
        if patches or manifest.get('fromVersion') is not None:
            raise ValueError('Baseline release must not offer an MSP')
        return
    name = f'OSCAR-UI-{version}-windows-x64.msp'
    url = f'https://github.com/mgarcia01752/oscar-ui/releases/download/v{version}/{name}'
    if (not manifest.get('fromVersion') or len(patches) != 1 or patches[0]['name'] != name
            or patches[0].get('state') != 'uploaded' or patch.get('url') != url
            or patches[0].get('size', 0) != patch.get('size') or patch.get('size', 0) <= 0
            or patches[0].get('digest') != patch.get('digest') or not re.fullmatch(r'sha256:[a-fA-F0-9]{64}', patch.get('digest', ''))):
        raise ValueError('Patch manifest does not match uploaded MSP')


def main():
    """Validate release JSON files and exit nonzero before any release publication."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--version', required=True)
    parser.add_argument('--assets', type=Path, required=True)
    parser.add_argument('--manifest', type=Path, required=True)
    args = parser.parse_args()
    verify(args.version, json.loads(args.assets.read_text(encoding='utf-8'))['assets'],
           json.loads(args.manifest.read_text(encoding='utf-8-sig')))
    print('Windows release assets verified')


if __name__ == '__main__':
    main()
