#!/usr/bin/env python3
# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
"""Download a published OSCAR thin JAR, update pins, and verify the local build."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
API = 'https://api.github.com/repos/Comcast/Oscar'
VERSION_PATTERN = r'(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)'
POM_NAMESPACE = {'m': 'http://maven.apache.org/POM/4.0.0'}


def main():
    """Parse the release selection and report failures without publishing anything."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--version', required=True, help='Published version, e.g. 3.2.1 (no v prefix)')
    parser.add_argument('--dry-run', action='store_true',
                        help='Download and verify in temporary storage; do not install or edit')
    args = parser.parse_args()
    try:
        _update(ROOT, args.version, args.dry_run)
    except (RuntimeError, OSError, ValueError, KeyError, ET.ParseError,
            zipfile.BadZipFile, subprocess.CalledProcessError) as error:
        parser.exit(1, f'ERROR: {error}\n')


def _progress(message):
    print(f'[OSCAR update] {message}', flush=True)


def _fetch(url):
    request = urllib.request.Request(url, headers={'User-Agent': 'OSCAR-UI-dependency-updater'})
    with urllib.request.urlopen(request, timeout=120) as response:
        return response.read()


def _api(path):
    return json.loads(_fetch(f'{API}/{path}'))


def _resolve(version):
    release = _api(f'releases/tags/v{version}')
    if release['tag_name'] != f'v{version}' or release['draft'] or release['prerelease']:
        raise RuntimeError('Select a published, stable OSCAR release.')
    assets = [asset for asset in release['assets'] if asset['name'] == 'oscar-thin.jar']
    if len(assets) != 1 or assets[0]['state'] != 'uploaded' or assets[0]['size'] <= 0:
        raise RuntimeError('Release must contain one uploaded, nonempty oscar-thin.jar.')
    reference = _api(f'git/ref/tags/v{version}')['object']
    for _ in range(10):
        if reference['type'] == 'commit':
            if not re.fullmatch(r'[0-9a-f]{40}', reference['sha']):
                raise RuntimeError('Invalid release commit SHA.')
            return release, assets[0], reference['sha']
        if reference['type'] != 'tag':
            break
        reference = _api(f'git/tags/{reference["sha"]}')['object']
    raise RuntimeError('Cannot resolve release tag to a commit.')


def _verify_jar(path, version):
    with zipfile.ZipFile(path) as archive:
        if archive.testzip() is not None:
            raise RuntimeError('JAR has a damaged ZIP entry.')
        pom = ET.fromstring(archive.read('META-INF/maven/com.comcast.oscar/oscar/pom.xml'))
        for field, expected in [('groupId', 'com.comcast.oscar'), ('artifactId', 'oscar'),
                                ('version', version)]:
            if pom.findtext(f'm:{field}', namespaces=POM_NAMESPACE) != expected:
                raise RuntimeError(f'Embedded OSCAR POM {field} does not match {expected}.')


def _verify_checksum(release, asset, digest):
    published = []
    if asset.get('digest'):
        published.append(asset['digest'])
    for checksum in release['assets']:
        if checksum['name'] not in ('SHA256SUMS.txt', 'SHA256SUMS', 'oscar-thin.jar.sha256'):
            continue
        content = _fetch(checksum['browser_download_url']).decode('utf-8')
        matches = re.findall(r'^([0-9a-fA-F]{64})\s+\*?(?:\./)?oscar-thin\.jar\s*$', content, re.M)
        if not matches and checksum['name'] == 'oscar-thin.jar.sha256':
            if re.fullmatch(r'\s*[0-9a-fA-F]{64}\s*', content):
                matches = [content.strip()]
        if not matches:
            raise RuntimeError('Published checksum file has no usable oscar-thin.jar checksum.')
        published.extend(f'sha256:{match.lower()}' for match in matches)
    if any(value.lower() != f'sha256:{digest}' for value in published):
        raise RuntimeError('Downloaded JAR does not match the published SHA-256.')
    _progress('Published checksum verified.' if published else
              'No published checksum found; pinning the SHA-256 of the HTTPS download.')


def _replace_once(text, pattern, replacement, name):
    updated, count = re.subn(pattern, lambda match: replacement, text, flags=re.M)
    if count != 1:
        raise RuntimeError(f'Expected one reference in {name}; found {count}. No files updated.')
    return updated


def _references(root, version, commit, digest):
    current = ET.parse(root / 'pom.xml').findtext('m:properties/m:oscar.version', namespaces=POM_NAMESPACE)
    if not current or not re.fullmatch(VERSION_PATTERN, current):
        raise RuntimeError('Cannot read current OSCAR version.')
    escaped = re.escape(current)
    specifications = {
        'pom.xml': [(rf'<oscar.version>{escaped}</oscar.version>', f'<oscar.version>{version}</oscar.version>')],
        'scripts/unix/bootstrap-oscar.sh': [
            (rf'^VERSION={escaped}$', f'VERSION={version}'),
            (r'^COMMIT=[0-9a-f]{40}$', f'COMMIT={commit}'),
            (r'^SHA256=[0-9a-f]{64}$', f'SHA256={digest}')],
        'scripts/windows/bootstrap-oscar.ps1': [
            (rf"^\$Version = '{escaped}'$", f"$Version = '{version}'"),
            (r"^\$Commit = '[0-9a-f]{40}'$", f"$Commit = '{commit}'"),
            (r"^\$Sha256 = '[0-9a-f]{64}'$", f"$Sha256 = '{digest}'")],
        'scripts/shared/desktop.py': [(rf"\.tools/oscar-{escaped}'", f".tools/oscar-{version}'")],
        'THIRD-PARTY-NOTICES.md': [
            (rf'^\| Comcast/Oscar {escaped}, commit [0-9a-f]{{40}} \|.*$',
             f'| Comcast/Oscar {version}, commit {commit} | Apache-2.0; https://github.com/Comcast/Oscar/releases/tag/v{version} |')],
        'docs/development/README.md': [
            (rf'\[Oscar v{escaped}\]\(https://github.com/Comcast/Oscar/releases/tag/v{escaped}\)',
             f'[Oscar v{version}](https://github.com/Comcast/Oscar/releases/tag/v{version})'),
            (rf'Tools → Check Shared Secret checks a selected binary through OSCAR {escaped}\.',
             f'Tools → Check Shared Secret checks a selected binary through OSCAR {version}.')],
        'docs/development/ui-architecture.md': [
            (rf'OSCAR {escaped} verifies the original bytes', f'OSCAR {version} verifies the original bytes')],
    }
    originals, updates = {}, {}
    for name, replacements in specifications.items():
        originals[name] = (root / name).read_bytes()
        text = originals[name].decode('utf-8')
        # Normalize for matching, then retain each file's existing newline style.
        newline = '\r\n' if '\r\n' in text else '\n'
        text = text.replace('\r\n', '\n')
        for pattern, replacement in replacements:
            text = _replace_once(text, pattern, replacement, name)
        updates[name] = text.replace('\n', newline).encode('utf-8')
    return current, originals, updates


def _maven(root, *arguments):
    wrapper = root / ('mvnw.cmd' if sys.platform == 'win32' else 'mvnw')
    subprocess.run([str(wrapper), '-B', f'-Dmaven.repo.local={root / ".m2"}', *arguments],
                   cwd=root, check=True)


def _apply(root, staging, originals, updates, version):
    cache = root / f'.tools/oscar-{version}'
    if cache.exists():
        for name in ('oscar-thin.jar', 'LICENSE', 'NOTICE'):
            if (cache / name).exists() and (cache / name).read_bytes() != (staging / name).read_bytes():
                raise RuntimeError(f'Existing release cache differs: {cache / name}. Refusing to overwrite.')
    _progress('Installing verified JAR into the project-local Maven repository...')
    _maven(root, 'org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file',
           f'-Dfile={staging / "oscar-thin.jar"}')
    cache.mkdir(parents=True, exist_ok=True)
    for name in ('oscar-thin.jar', 'LICENSE', 'NOTICE'):
        shutil.copy2(staging / name, cache / name)
    for name in updates:
        if (root / name).read_bytes() != originals[name]:
            raise RuntimeError(f'{name} changed during the update; retry after finishing other edits.')
    try:
        for name, contents in updates.items():
            _progress(f'Updating {name}')
            (root / name).write_bytes(contents)
        _progress('Running Maven clean verify (build and Java tests)...')
        _maven(root, 'clean', 'verify')
    except (Exception, KeyboardInterrupt):
        _progress('Update failed; restoring original reference files. Download/build caches may remain.')
        for name, contents in originals.items():
            (root / name).write_bytes(contents)
        raise


def _update(root, version, dry_run):
    if not re.fullmatch(VERSION_PATTERN, version):
        raise RuntimeError('Use MAJOR.MINOR.PATCH without a v prefix.')
    _progress(f'Resolving published OSCAR v{version}...')
    release, asset, commit = _resolve(version)
    _progress(f'Resolved commit: {commit}')
    with tempfile.TemporaryDirectory(prefix='oscar-update-') as directory:
        staging = Path(directory)
        _progress(f'Downloading oscar-thin.jar ({asset["size"]:,} bytes)...')
        jar = _fetch(asset['browser_download_url'])
        if len(jar) != asset['size']:
            raise RuntimeError('Downloaded JAR size differs from release metadata.')
        digest = hashlib.sha256(jar).hexdigest()
        _verify_checksum(release, asset, digest)
        (staging / 'oscar-thin.jar').write_bytes(jar)
        _verify_jar(staging / 'oscar-thin.jar', version)
        _progress(f'JAR validated. SHA-256: {digest}')
        for name in ('LICENSE', 'NOTICE'):
            _progress(f'Downloading {name} at the pinned commit...')
            content = _fetch(f'https://raw.githubusercontent.com/Comcast/Oscar/{commit}/{name}')
            if not content.strip():
                raise RuntimeError(f'Empty upstream {name}.')
            (staging / name).write_bytes(content)
        current, originals, updates = _references(root, version, commit, digest)
        if tuple(map(int, version.split('.'))) < tuple(map(int, current.split('.'))):
            raise RuntimeError('Refusing to downgrade OSCAR.')
        if version == current and any(originals[name] != updates[name] for name in updates):
            raise RuntimeError('Existing version has different pins; refusing to repin a release.')
        _progress(f'OSCAR {current} -> {version}; {len(updates)} reference files checked.')
        if dry_run:
            for name in updates:
                _progress(f'Would update: {name}')
            _progress('Dry run complete. No repository files, caches, or Maven artifacts changed.')
            return
        _apply(root, staging, originals, updates, version)
    _progress('Update complete. Maven build/tests passed; native GUI checks remain separate.')
    _progress('Review git diff, then rebuild the desktop image before running the UI. Nothing committed or pushed.')


if __name__ == '__main__':
    main()
