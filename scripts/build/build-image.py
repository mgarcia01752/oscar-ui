#!/usr/bin/env python3
# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
"""Build a local native installer using the existing platform release scripts."""
import argparse
from pathlib import Path
import platform
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]


def main(argv=None):
    """Build the requested host installer and report its local output path.

    Require one platform option; --all means all supported targets on this host.
    Preview with --dry-run. Preserve existing output and child failure status.
    Do not install dependencies, install the application, or publish a release.
    """
    parser = argparse.ArgumentParser(description=__doc__)
    targets = parser.add_mutually_exclusive_group(required=True)
    targets.add_argument('--linux', action='store_true', help='Build an Ubuntu x64 DEB on Linux')
    targets.add_argument('--windows', action='store_true', help='Build a Windows x64 MSI on Windows')
    targets.add_argument('--macos', action='store_true', help='Build an experimental Apple Silicon DMG on macOS')
    targets.add_argument('--all', action='store_true', help='Build all supported installers for this host only')
    parser.add_argument('--dry-run', action='store_true', help='Show the build command and output without executing')
    arguments = parser.parse_args(argv)
    requested = 'linux' if arguments.linux else 'windows' if arguments.windows else 'macos' if arguments.macos else 'all'
    try:
        target, command, artifact, build_directories = _plan(requested)
        print(f'[build-image] Target: {target}; local build only', flush=True)
        if requested == 'all':
            print('[build-image] --all selects the current host only; no cross-platform build.', flush=True)
        print(f'[build-image] Command: {subprocess.list2cmdline(command)}', flush=True)
        print(f'[build-image] Output: {artifact}', flush=True)
        if arguments.dry_run:
            print('[build-image] Preview complete; no build was run.', flush=True)
            return 0
        for path in [*build_directories, artifact]:
            if path.exists():
                raise RuntimeError(f'Output already exists: {path}. Move it aside before rebuilding.')
        if not shutil.which(command[0]):
            raise RuntimeError(f'{command[0]} is required; see docs/development/build/build-images.md')
        print('[build-image] Building and checking the installer; dependency sources may be downloaded.', flush=True)
        subprocess.run(command, cwd=ROOT, check=True)
        if not artifact.is_file() or artifact.stat().st_size == 0:
            raise RuntimeError(f'Build returned success but the installer is missing or empty: {artifact}')
        print(f'[build-image] COMPLETE: {artifact} ({artifact.stat().st_size:,} bytes)', flush=True)
        return 0
    except subprocess.CalledProcessError as error:
        print(f'[build-image] FAILED: packaging exited with status {error.returncode}', file=sys.stderr)
        return error.returncode if error.returncode > 0 else 1
    except (RuntimeError, OSError, ET.ParseError) as error:
        print(f'[build-image] ERROR: {error}', file=sys.stderr)
        return 1


def _plan(requested):
    hosts = {'linux': 'linux', 'win32': 'windows', 'darwin': 'macos'}
    host = hosts.get(sys.platform)
    architectures = ('arm64',) if host == 'macos' else ('x86_64', 'amd64')
    if host is None or platform.machine().lower() not in architectures:
        raise RuntimeError('Unsupported host: require Linux/Windows x64 or macOS arm64.')
    target = host if requested == 'all' else requested
    if target != host:
        raise RuntimeError(f'Cannot build {target} on {host}. Run this command on a supported {target} host.')
    version_node = ET.parse(ROOT / 'pom.xml').getroot().find('{http://maven.apache.org/POM/4.0.0}version')
    if version_node is None or not version_node.text:
        raise RuntimeError('Missing project version in pom.xml')
    version = version_node.text.strip()
    if target == 'linux':
        command = ['bash', (ROOT / 'scripts/unix/linux-release.sh').as_posix()]
        directories = [ROOT / 'dist/linux/deb']
        artifact = ROOT / 'dist/linux/release/oscar-ui_amd64.deb'
    elif target == 'macos':
        command = [sys.executable, (ROOT / 'scripts/build/macos-release.py').as_posix()]
        directories = [ROOT / 'dist/macos']
        artifact = ROOT / f'dist/macos/release/OSCAR-UI-{version}-macos-arm64.dmg'
    else:
        command = ['pwsh', '-NoProfile', '-File', (ROOT / 'scripts/windows/windows-release.ps1').as_posix()]
        directories = [ROOT / 'dist/windows/app-image', ROOT / 'dist/windows/msi']
        artifact = ROOT / f'dist/windows/release/OSCAR-UI-{version}-windows-x64.msi'
    return target, command, artifact, directories


if __name__ == '__main__':
    raise SystemExit(main())
