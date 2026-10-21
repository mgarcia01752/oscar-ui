#!/usr/bin/env python3
# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
"""Build and verify an experimental Apple Silicon DMG without Developer ID."""
import importlib.util
import os
from pathlib import Path
import platform
import plistlib
import re
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def bundle_build_version(version):
    """Derive a positive macOS build version from the stable POM version.

    Offset every major version by one: 0.6.0 becomes 1.6.0, and 1.0.0
    becomes 2.0.0. This satisfies jpackage without collisions at 1.0.
    """
    if not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+', version):
        raise ValueError(f'Expected a stable major.minor.patch version: {version}')
    major, minor, patch = map(int, version.split('.'))
    return f'{major + 1}.{minor}.{patch}'


def set_bundle_versions(application, version):
    """Keep the displayed POM version and derived build number before signing."""
    path = application / 'Contents/Info.plist'
    with path.open('rb') as source:
        metadata = plistlib.load(source)
    metadata['CFBundleShortVersionString'] = version
    metadata['CFBundleVersion'] = bundle_build_version(version)
    with path.open('wb') as destination:
        plistlib.dump(metadata, destination)


def run(arguments, **kwargs):
    """Run a checked build command from the repository and report progress."""
    print('[macOS] ' + ' '.join(map(str, arguments)), flush=True)
    return subprocess.run(list(map(str, arguments)), cwd=ROOT, check=True, **kwargs)


def extract_frameworks(archive, destination):
    """Extract Qt Jambi frameworks and restore manifest-declared relative links.

    Reject entries escaping the destination. Return each framework executable
    so macdeployqt can resolve its Qt dependencies before signing the bundle.
    """
    def relative_path(name):
        path = Path(name)
        if path.is_absolute() or '..' in path.parts or not path.parts or path.parts[0] != 'lib':
            raise ValueError(f'Invalid native archive path: {name}')
        return path.relative_to('lib')

    with zipfile.ZipFile(archive) as source:
        for item in source.infolist():
            if item.filename.startswith('lib/') and not item.is_dir():
                target = destination / relative_path(item.filename)
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(source.read(item))
        manifest = ET.fromstring(source.read('META-INF/qtjambi-deployment.xml'))
        for entry in manifest.findall('symlink'):
            link = destination / relative_path(entry.attrib['name'])
            target = destination / relative_path(entry.attrib['target'])
            link.parent.mkdir(parents=True, exist_ok=True)
            link.symlink_to(os.path.relpath(target, link.parent))
    executables = []
    for framework in sorted(destination.glob('QtJambi*.framework')):
        executable = framework / 'Versions/6' / framework.stem
        if not executable.is_file():
            raise RuntimeError(f'Missing framework executable: {executable}')
        executable.chmod(0o755)
        executables.append(executable)
    if not executables:
        raise RuntimeError('No Qt Jambi frameworks extracted')
    return executables


def smoke(application):
    """Launch the packaged application with its bundled JVM and Cocoa plugin.

    Remove developer library overrides and require a successful smoke-test exit.
    """
    environment = {key: value for key, value in os.environ.items()
                   if not key.startswith(('QT_', 'DYLD_', 'JAVA_', 'JDK_'))}
    environment.pop('_JAVA_OPTIONS', None)
    environment['PATH'] = '/usr/bin:/bin:/usr/sbin:/sbin'
    run([application / 'Contents/MacOS/OSCAR-UI', '--smoke-test',
         ROOT / 'src/test/resources/simple-docsis.txt'], env=environment, timeout=90)


def build():
    """Build, ad-hoc sign, mount, copy, and smoke-test a self-contained DMG.

    Require native Apple Silicon and Java 21. Preserve prior release outputs.
    No system installation, Git push, or release publication is performed.
    """
    if sys.platform != 'darwin' or platform.machine().lower() != 'arm64':
        raise RuntimeError('Build on an Apple Silicon macOS runner; cross-compilation is unsupported.')
    pom = ET.parse(ROOT / 'pom.xml')
    version = pom.findtext('{*}version')
    build_version = bundle_build_version(version)
    qt_version = pom.findtext('{*}properties/{*}qtjambi.version')
    qt = ROOT / '.tools/Qt' / qt_version / 'macos'
    if not (qt / 'bin/macdeployqt').is_file():
        raise RuntimeError('Run bash scripts/macos/setup-qt.sh first.')
    output = ROOT / 'dist/macos'
    if output.exists():
        raise RuntimeError(f'Output exists; move it aside first: {output}')
    spec = importlib.util.spec_from_file_location('desktop', ROOT / 'scripts/build/desktop.py')
    desktop = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(desktop)
    java_version = run([desktop.java_tool('java'), '-version'], capture_output=True, text=True)
    if 'version "21.' not in java_version.stderr:
        raise RuntimeError('Java 21 is required.')
    desktop.build()
    run([sys.executable, ROOT / 'scripts/build/collect-release-sources.py'])
    image = output / 'image'
    application = image / 'OSCAR-UI.app'
    iconset = ROOT / 'target/oscar-ui.iconset'
    iconset.mkdir()
    for size in (16, 32, 128, 256, 512):
        for scale in (1, 2):
            suffix = '@2x' if scale == 2 else ''
            run(['sips', '-z', size * scale, size * scale,
                 ROOT / 'docs/images/oscar-ui-light-icon.png', '--out',
                 iconset / f'icon_{size}x{size}{suffix}.png'])
    icon = ROOT / 'target/oscar-ui.icns'
    run(['iconutil', '-c', 'icns', iconset, '-o', icon])
    run([desktop.java_tool('jpackage'), '--type', 'app-image', '--name', 'OSCAR-UI',
         '--app-version', build_version, '--input', ROOT / 'target/app', '--dest', image,
         '--main-jar', f'oscar-ui-{version}.jar', '--main-class', 'io.oscarui.DesktopApplication',
         '--icon', icon, '--mac-package-identifier', 'io.oscarui.desktop',
         '--java-options', '-XstartOnFirstThread',
         '--java-options', '-Djava.library.path=$APPDIR/../Frameworks',
         '--java-options', '-Dio.qt.pluginpath=$APPDIR/../PlugIns'])
    frameworks = application / 'Contents/Frameworks'
    frameworks.mkdir(exist_ok=True)
    native = application / f'Contents/app/lib/qtjambi-native-macos-{qt_version}.jar'
    executables = extract_frameworks(native, frameworks)
    # Explicit JNI executables are required: the Java launcher does not link Qt.
    run([qt / 'bin/macdeployqt', application, '-always-overwrite',
         *[f'-executable={executable}' for executable in executables]])
    for executable in executables:
        # The JNI libraries must resolve sibling frameworks from the launcher.
        commands = run(['otool', '-l', executable], capture_output=True, text=True).stdout
        if 'path @executable_path/../Frameworks (' not in commands:
            run(['install_name_tool', '-add_rpath', '@executable_path/../Frameworks', executable])
    if not (application / 'Contents/PlugIns/platforms/libqcocoa.dylib').is_file():
        raise RuntimeError('macdeployqt did not bundle the Cocoa platform plugin.')
    run([sys.executable, ROOT / 'scripts/build/verify-java-distribution.py',
         '--runtime', application / 'Contents/runtime/Contents/Home',
         '--sources', application / 'Contents/app/sources', '--jdk-home', os.environ['JAVA_HOME']])
    set_bundle_versions(application, version)
    run(['codesign', '--force', '--deep', '--sign', '-', application])
    run(['codesign', '--verify', '--deep', '--strict', application])
    release = output / 'release'
    release.mkdir()
    dmg = release / f'OSCAR-UI-{version}-macos-arm64.dmg'
    (image / 'Applications').symlink_to('/Applications')
    run(['hdiutil', 'create', '-volname', 'OSCAR-UI', '-srcfolder', image,
         '-format', 'UDZO', dmg])
    # Test the shipped disk image, not the staging copy. Hide build Qt so a
    # host dependency cannot accidentally satisfy the packaged application.
    hidden_qt = qt.with_name(qt.name + '-package-test')
    if hidden_qt.exists():
        raise RuntimeError(f'Restore the previous Qt test directory first: {hidden_qt}')
    qt.rename(hidden_qt)
    try:
        with tempfile.TemporaryDirectory(prefix='oscar-macos-install-') as directory:
            root = Path(directory)
            mount = root / 'mount'
            mount.mkdir()
            run(['hdiutil', 'attach', dmg, '-readonly', '-nobrowse', '-mountpoint', mount])
            try:
                installed = root / 'Applications/OSCAR-UI.app'
                installed.parent.mkdir()
                run(['ditto', mount / 'OSCAR-UI.app', installed])
            finally:
                run(['hdiutil', 'detach', mount])
            fixture = root / 'user-configuration.txt'
            fixture.write_text('Synthetic user configuration', encoding='utf-8')
            run(['codesign', '--verify', '--deep', '--strict', installed])
            smoke(installed)
            shutil.rmtree(installed)
            if installed.exists() or fixture.read_text(encoding='utf-8') != 'Synthetic user configuration':
                raise RuntimeError('Removal did not preserve external user files.')
    finally:
        hidden_qt.rename(qt)
    print(f'[macOS] Verified experimental DMG: {dmg}', flush=True)


if __name__ == '__main__':
    try:
        build()
    except (RuntimeError, OSError, ValueError, subprocess.SubprocessError) as error:
        raise SystemExit(f'ERROR: {error}')
