#!/usr/bin/env python3
# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
"""Check Java redistribution materials in an extracted or installed package."""
import argparse
import hashlib
from pathlib import Path
import re
import tarfile


def verify(runtime: Path, sources: Path, jdk_home: Path | None = None):
    """Verify bundled Java notices and corresponding source materials.

    Accept an extracted runtime and its application's sources directory.
    Optionally compare all retained module notices against the build JDK.
    Raise RuntimeError for missing, inconsistent, or damaged materials.
    This checks distribution evidence, not legal compliance certification.
    """
    release = (runtime / 'release').read_text(encoding='utf-8')
    version = re.search(r'^JAVA_VERSION="([\d.]+)"$', release, re.M)
    modules = re.search(r'^MODULES="([^"]+)"$', release, re.M)
    if not version or not modules:
        raise RuntimeError('Expected a Java runtime with version and module metadata')
    license_text = (runtime / 'legal/java.base/LICENSE').read_text(encoding='utf-8')
    if 'GNU GENERAL PUBLIC LICENSE' not in license_text.upper() or 'CLASSPATH' not in license_text.upper():
        raise RuntimeError('Java GPLv2 / Classpath Exception text is missing')
    # jpackage can omit duplicate per-module symlinks in DEBs. Require every
    # original notice text to survive, allowing an identical shared copy.
    retained_notices = {notice.read_bytes() for notice in (runtime / 'legal').rglob('*')
                        if notice.is_file()}
    for module in modules.group(1).split():
        notices = runtime / 'legal' / module
        if not notices.is_dir():
            raise RuntimeError(f'Missing Java module notices: {module}')
        for notice in notices.rglob('*'):
            if not notice.is_dir() and (not notice.is_file() or not notice.stat().st_size):
                raise RuntimeError(f'Empty or broken Java notice: {notice}')
        if jdk_home is not None:
            originals = jdk_home / 'legal' / module
            if not originals.is_dir():
                raise RuntimeError(f'Build JDK has no notices for {module}')
            for original in originals.rglob('*'):
                if original.is_file():
                    bundled = notices / original.relative_to(originals)
                    if original.read_bytes() not in retained_notices:
                        raise RuntimeError(f'Java notice differs from build JDK: {bundled}')
    java_version = version.group(1)
    major_version = java_version.split('.')[0]
    archives = list(sources.glob(f'OpenJDK{major_version}U-jdk-sources_{java_version}_*.tar.gz'))
    if len(archives) != 1:
        raise RuntimeError('Expected exactly one source archive matching the runtime version')
    archive = archives[0]
    archive_name = archive.name
    if jdk_home is not None:
        build_release = (jdk_home / 'release').read_text(encoding='utf-8')
        build_version = re.search(r'^JAVA_RUNTIME_VERSION="([\d.]+)\+(\d+)(?:-LTS)?"$', build_release, re.M)
        if 'IMPLEMENTOR="Eclipse Adoptium"' not in build_release or not build_version:
            raise RuntimeError('Build JDK must be Eclipse Temurin')
        expected = f'OpenJDK{major_version}U-jdk-sources_{build_version[1]}_{build_version[2]}.tar.gz'
        if archive_name != expected:
            raise RuntimeError('Java source archive does not match the build JDK')
    manifest = (sources / 'SHA256SUMS.txt').read_text(encoding='utf-8')
    with archive.open('rb') as stream:
        digest = hashlib.sha256()
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(chunk)
    if f'{digest.hexdigest()}  {archive_name}' not in manifest.splitlines():
        raise RuntimeError('Java source archive checksum does not match manifest')
    with tarfile.open(archive, 'r:gz') as source_archive:
        names = {member.name.removeprefix('./') for member in source_archive if member.isfile()}
    # Vendor archives have a single enclosing directory. Do not extract them.
    required = ('LICENSE', 'ADDITIONAL_LICENSE_INFO', 'ASSEMBLY_EXCEPTION',
                'configure', 'doc/building.md', 'src/java.base/share/classes/java/lang/Object.java')
    for suffix in required:
        if not any(name == suffix or name.endswith('/' + suffix) for name in names):
            raise RuntimeError(f'Java source archive lacks {suffix}')
    if not any('/make/' in '/' + name for name in names):
        raise RuntimeError('Java source archive lacks build scripts')
    print(f'JAVA_DISTRIBUTION_OK: Java {java_version}; licenses, sources and checksum')


def main():
    """Parse package paths and fail the release if Java materials are invalid.

    Run without installing the package; Windows CI also checks installed files.
    """
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runtime', required=True, type=Path)
    parser.add_argument('--sources', required=True, type=Path)
    parser.add_argument('--jdk-home', type=Path)
    arguments = parser.parse_args()
    verify(arguments.runtime, arguments.sources, arguments.jdk_home)


if __name__ == '__main__':
    main()
