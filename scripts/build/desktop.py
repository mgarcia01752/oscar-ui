#!/usr/bin/env python3
# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
"""Cross-platform build, run, smoke-test and native jpackage entry point."""
import argparse
import os
from pathlib import Path
import platform
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
WINDOWS = sys.platform == 'win32'
QT_VERSION = ET.parse(ROOT / 'pom.xml').findtext('{*}properties/{*}qtjambi.version')
VERSION = ET.parse(ROOT / 'pom.xml').getroot().find(
    '{http://maven.apache.org/POM/4.0.0}version').text


def run(args, **kwargs):
    subprocess.run([str(a) for a in args], check=True, cwd=ROOT, **kwargs)


def java_tool(name):
    home = os.environ.get('JAVA_HOME')
    tool = Path(home) / 'bin' / (name + ('.exe' if WINDOWS else '')) if home else shutil.which(name)
    if not tool or not Path(tool).exists():
        raise RuntimeError(f'{name} not found. Configure JAVA_HOME for a Java 21 JDK.')
    return str(tool)


def verify_platform():
    if sys.platform not in ('linux', 'win32') or platform.machine().lower() not in ('x86_64', 'amd64'):
        raise RuntimeError('Only native Linux and Windows x86_64 builds are supported.')
    result = subprocess.run([java_tool('java'), '-version'], capture_output=True, text=True, check=True)
    if 'version "21.' not in result.stderr:
        raise RuntimeError('Use a Java 21 JDK for both Maven and jpackage.')


def qt_home():
    default = ROOT / '.tools/Qt' / QT_VERSION / ('msvc2022_64' if WINDOWS else 'gcc_64')
    qt = Path(os.environ.get('QT_HOME', str(default))).resolve()
    qmake = qt / 'bin' / ('qmake.exe' if WINDOWS else 'qmake')
    result = subprocess.run([qmake, '-query', 'QT_VERSION'], capture_output=True, text=True, check=True)
    if result.stdout.strip() != QT_VERSION:
        raise RuntimeError(f'Expected Qt {QT_VERSION}; check QT_HOME.')
    return qt


def build():
    wrapper = ROOT / ('mvnw.cmd' if WINDOWS else 'mvnw')
    run([wrapper, '-B', f'-Dmaven.repo.local={ROOT / ".m2"}', 'clean', 'verify'])
    app = ROOT / 'target/app'
    shutil.copy2(ROOT / f'target/oscar-ui-{VERSION}.jar', app)
    for name in ('LICENSE', 'NOTICE', 'THIRD-PARTY-NOTICES.md'):
        shutil.copy2(ROOT / name, app)
    shutil.copytree(ROOT / 'licenses', app / 'licenses', dirs_exist_ok=True)
    if sys.platform == 'linux':
        for helper in ('ubuntu-update.py', 'ubuntu-uninstall.py'):
            shutil.copy2(ROOT / 'scripts/unix' / helper, app / helper)
    # Keep the pinned original Oscar attribution alongside its unmodified library.
    upstream = ROOT / '.tools/oscar-3.7.5'
    if not (upstream / 'LICENSE').exists():
        raise RuntimeError('Run bootstrap-oscar first to provide the pinned release and notices.')
    shutil.copy2(upstream / 'LICENSE', app / 'licenses/Oscar-LICENSE.txt')
    if (upstream / 'NOTICE').exists():
        shutil.copy2(upstream / 'NOTICE', app / 'licenses/Oscar-NOTICE.txt')


def stage_qt():
    qt = qt_home()
    dest = ROOT / 'target/app/qt'
    if dest.exists():
        shutil.rmtree(dest)
    (dest / 'lib').mkdir(parents=True)
    names = ['Core', 'Gui', 'Widgets'] + ([] if WINDOWS else ['DBus', 'XcbQpa'])
    for name in names:
        source = qt / ('bin' if WINDOWS else 'lib')
        files = list(source.glob(f'Qt6{name}.dll' if WINDOWS else f'libQt6{name}.so*'))
        if not files:
            raise RuntimeError(f'Missing Qt {name} shared library in {source}')
        for file in files:
            shutil.copy2(file, dest / 'lib' / file.name)
    if not WINDOWS:
        for prefix in ('icudata', 'icui18n', 'icuuc'):
            files = list((qt / 'lib').glob(f'lib{prefix}.so*'))
            if not files:
                raise RuntimeError('Missing ICU libraries; install the pinned Qt icu archive.')
            for file in files:
                shutil.copy2(file, dest / 'lib' / file.name)
    for category in ('platforms', 'imageformats', 'styles'):
        source = qt / 'plugins' / category
        if not source.exists():
            continue
        target = dest / 'plugins' / category
        target.mkdir(parents=True)
        for file in source.glob('*.dll' if WINDOWS else '*.so'):
            if WINDOWS and file.stem.endswith('d'):
                continue  # MSVC debug plugins require a different runtime.
            if category == 'platforms' and file.name not in (
                    'qwindows.dll', 'qoffscreen.dll', 'qminimal.dll',
                    'libqxcb.so', 'libqoffscreen.so', 'libqminimal.so'):
                continue
            shutil.copy2(file, target / file.name)
    if WINDOWS:
        for name in ('opengl32sw.dll', 'd3dcompiler_47.dll'):
            if (qt / 'bin' / name).exists():
                shutil.copy2(qt / 'bin' / name, dest / 'lib' / name)


def launch(smoke):
    app = ROOT / 'target/app'
    if not (app / f'oscar-ui-{VERSION}.jar').exists():
        raise RuntimeError('Run the build command first.')
    stage_qt()
    env = os.environ.copy()
    if smoke:
        env.setdefault('QT_QPA_PLATFORM', 'offscreen')
    options = [f'-Djava.library.path={app / "qt/lib"}', f'-Dio.qt.pluginpath={app / "qt/plugins"}',
               '-Dio.qt.enable-thread-affinity-check=true']
    args = [java_tool('java'), *options, '-jar', app / f'oscar-ui-{VERSION}.jar']
    if smoke:
        args += ['--smoke-test', ROOT / 'src/test/resources/simple-docsis.txt']
    run(args, env=env, timeout=40 if smoke else None)


def package(kind):
    app = ROOT / 'target/app'
    if not (app / f'oscar-ui-{VERSION}.jar').exists():
        raise RuntimeError('Run build first.')
    stage_qt()
    if kind not in (('app-image', 'msi', 'exe') if WINDOWS else ('app-image', 'deb', 'rpm')):
        raise RuntimeError('Installer type must match the host OS.')
    destination = ROOT / 'dist' / ('windows' if WINDOWS else 'linux') / kind
    if destination.exists():
        raise RuntimeError(f'Output already exists: {destination}; move or delete it before rebuilding.')
    args = [java_tool('jpackage'), '--type', kind, '--name', 'OSCAR-UI', '--app-version', VERSION,
            '--vendor', 'OSCAR-UI contributors', '--input', app, '--dest', destination,
            '--icon', ROOT / 'docs/images' / ('oscar-ui.ico' if WINDOWS else 'oscar-ui-light-icon.png'),
            '--main-jar', f'oscar-ui-{VERSION}.jar', '--main-class', 'io.oscarui.DesktopApplication',
            '--add-modules', 'java.base,java.desktop,java.logging,java.xml,java.sql,java.naming,java.management,jdk.unsupported,jdk.crypto.ec',
            '--java-options', '-Djava.library.path=$APPDIR/qt/lib',
            '--java-options', '-Dio.qt.pluginpath=$APPDIR/qt/plugins']
    if kind != 'app-image':
        args += ['--license-file', ROOT / 'LICENSE']
        if WINDOWS:
            args += ['--win-menu', '--win-shortcut', '--win-dir-chooser', '--win-upgrade-uuid', '80186748-531f-4a39-a6da-f5c81a1f0730']
        else:
            args += ['--resource-dir', ROOT / 'packaging/linux',
                     '--linux-package-name', 'oscar-ui', '--linux-shortcut',
                     '--install-dir', '/opt']
            if kind == 'deb':
                args += ['--linux-package-deps',
                         'python3, pkexec, zenity, libgl1, libegl1, libdbus-1-3, libfontconfig1, libfreetype6, '
                         'libx11-xcb1, libxkbcommon0, libxkbcommon-x11-0, libxcb1, '
                         'libxcb-cursor0, libxcb-icccm4, libxcb-image0, libxcb-keysyms1, '
                         'libxcb-render-util0, libxcb-shape0, libxcb-randr0, '
                         'libxcb-xfixes0, libxcb-sync1, libxcb-render0']
    run(args)
    if kind == 'deb':
        for package_file in (ROOT / 'dist/linux/deb').glob('*.deb'):
            run([sys.executable, ROOT / 'scripts/build/deb-dependencies.py', package_file])


def main():
    """Dispatch desktop commands, optionally rebuilding before interactive launch.

    Reject conflicting modes before platform checks. A failed build propagates
    without launching the existing application. Use the current GUI environment.
    """
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', nargs='?', choices=['build', 'run', 'smoke', 'package'])
    parser.add_argument('--build-then-run', action='store_true',
                        help='Build and test the application, then launch it on the current display')
    parser.add_argument('--type', default='app-image', choices=['app-image', 'deb', 'rpm', 'msi', 'exe'])
    args = parser.parse_args()
    if args.build_then_run and args.command is not None:
        parser.error('Use --build-then-run without a separate command.')
    if not args.build_then_run and args.command is None:
        parser.error('Choose a command or --build-then-run.')
    if args.build_then_run or args.command == 'run':
        _require_display()
    verify_platform()
    if args.build_then_run:
        print('[desktop] Building and testing the application...', flush=True)
        build()
        print('[desktop] Build complete; launching the updated UI...', flush=True)
        launch(False)
    elif args.command == 'build':
        build()
    elif args.command == 'package':
        package(args.type)
    else:
        launch(args.command == 'smoke')


def _require_display():
    if (sys.platform == 'linux' and not os.environ.get('DISPLAY')
            and os.environ.get('QT_QPA_PLATFORM') not in ('offscreen', 'minimal')):
        raise RuntimeError(
            'DISPLAY is not set. Open a terminal in the VM graphical desktop, or set '
            'DISPLAY and XAUTHORITY for its active session before running this command. '
            'See docs/development/README.md#run-on-the-vm-desktop-from-a-remote-terminal. '
            'No build or UI launch was started.')


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, OSError, subprocess.SubprocessError) as error:
        raise SystemExit(f'ERROR: {error}')
