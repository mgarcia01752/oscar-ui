#!/usr/bin/env python3
"""Bundle corresponding sources inside a release application, not as extra assets."""
import hashlib
import os
from pathlib import Path
import shutil
import sys
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
SOURCES = {
    'qtbase-everywhere-src-6.8.3.tar.xz': 'https://download.qt.io/archive/qt/6.8/6.8.3/submodules/qtbase-everywhere-src-6.8.3.tar.xz',
    'qtjambi-6.8.3.tar.gz': 'https://github.com/OmixVisualization/qtjambi/archive/refs/tags/v6.8.3.tar.gz',
    'OpenJDK21U-jdk-sources_21.0.10_7.tar.gz': 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.10%2B7/OpenJDK21U-jdk-sources_21.0.10_7.tar.gz',
}


def collect():
    java = shutil.which('java')
    home = Path(os.environ['JAVA_HOME']) if os.environ.get('JAVA_HOME') else Path(java or '').resolve().parent.parent
    release = (home / 'release').read_text()
    if 'IMPLEMENTOR="Eclipse Adoptium"' not in release or 'JAVA_RUNTIME_VERSION="21.0.10+7-LTS"' not in release:
        raise RuntimeError('Release source bundling requires Temurin 21.0.10+7; keep JDK and source pins together.')
    app = ROOT / 'target/app'
    if not app.is_dir():
        raise RuntimeError('Build the application before collecting release sources.')
    sources = dict(SOURCES)
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
        hashes.append(f'{digest}  {name}\n')
    (destination / 'SHA256SUMS.txt').write_text(''.join(hashes))


if __name__ == '__main__':
    collect()
