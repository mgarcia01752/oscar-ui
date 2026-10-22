#!/usr/bin/env python3
# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
"""Bundle corresponding sources inside a release application, not as extra assets."""
import hashlib
import json
import os
import re
from pathlib import Path
import shutil
import sys
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
SOURCES = {
    'qtbase-everywhere-src-6.8.3.tar.xz': 'https://download.qt.io/archive/qt/6.8/6.8.3/submodules/qtbase-everywhere-src-6.8.3.tar.xz',
    'qtjambi-6.8.3.tar.gz': 'https://github.com/OmixVisualization/qtjambi/archive/refs/tags/v6.8.3.tar.gz',
}


def collect():
    """Bundle matching dependency sources for distribution with the application.

    Reject a mismatched JDK or Java source digest before packaging a release.
    """
    pin = json.loads((ROOT / 'scripts/build/release-java.json').read_text(encoding='utf-8'))
    version = pin['version']
    if not re.fullmatch(r'21\.0\.[0-9]+(?:\.[0-9]+)?\+[0-9]+', version):
        raise RuntimeError('Invalid release Java 21 version pin')
    if not re.fullmatch(r'[0-9a-f]{64}', pin['source_sha256']):
        raise RuntimeError('Invalid release Java source checksum')
    feature_version, build_number = version.split('+')
    source_name = f'OpenJDK21U-jdk-sources_{feature_version}_{build_number}.tar.gz'
    java = shutil.which('java')
    home = Path(os.environ['JAVA_HOME']) if os.environ.get('JAVA_HOME') else Path(java or '').resolve().parent.parent
    release = (home / 'release').read_text(encoding='utf-8')
    if 'IMPLEMENTOR="Eclipse Adoptium"' not in release or f'JAVA_RUNTIME_VERSION="{version}-LTS"' not in release:
        raise RuntimeError(f'Release source bundling requires Temurin {version}; keep JDK and source pins together.')
    app = ROOT / 'target/app'
    if not app.is_dir():
        raise RuntimeError('Build the application before collecting release sources.')
    sources = dict(SOURCES)
    tag = version.replace('+', '%2B')
    sources[source_name] = f'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-{tag}/{source_name}'
    if sys.platform == 'linux':
        sources['icu4c-73_2-src.tgz'] = 'https://github.com/unicode-org/icu/releases/download/release-73-2/icu4c-73_2-src.tgz'
    destination = app / 'sources'
    destination.mkdir(exist_ok=True)
    hashes = []
    for name, url in sources.items():
        temporary = destination / (name + '.download')
        try:
            with urllib.request.urlopen(url, timeout=120) as response, temporary.open('wb') as output:
                shutil.copyfileobj(response, output)
            temporary.replace(destination / name)
        finally:
            temporary.unlink(missing_ok=True)
        digest = hashlib.sha256((destination / name).read_bytes()).hexdigest()
        if name.startswith('OpenJDK') and digest != pin['source_sha256']:
            (destination / name).unlink()
            raise RuntimeError('Java source archive differs from the pinned vendor checksum')
        hashes.append(f'{digest}  {name}\n')
    (destination / 'SHA256SUMS.txt').write_text(''.join(hashes), encoding='utf-8')


if __name__ == '__main__':
    collect()
