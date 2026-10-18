#!/usr/bin/env python3
# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
"""Place Windows Qt libraries beside the launcher without duplicate copies."""
import argparse
from pathlib import Path

QT_LIBRARIES = ('Qt6Core.dll', 'Qt6Gui.dll', 'Qt6Widgets.dll')
OLD_OPTION = b'java-options=-Djava.library.path=$APPDIR/qt/lib'
NEW_OPTION = b'java-options=-Djava.library.path=$APPDIR/..'


def verify(image):
    """Check a prepared or installed image without modifying it.

    Require root Qt libraries, a relative launcher library path, and no remaining
    copies under app/qt/lib. Raise RuntimeError for an invalid layout.
    """
    for name in QT_LIBRARIES:
        if not (image / name).is_file():
            raise RuntimeError(f'Missing launcher-side library: {name}')
    if list((image / 'app/qt/lib').glob('*.dll')):
        raise RuntimeError('Duplicate native library directory: app/qt/lib')
    configuration = (image / 'app/OSCAR-UI.cfg').read_bytes()
    if configuration.count(NEW_OPTION) != 1 or OLD_OPTION in configuration:
        raise RuntimeError('Launcher must load native libraries from $APPDIR/..')
    if not (image / 'app/qt/plugins/platforms/qwindows.dll').is_file():
        raise RuntimeError('Missing Windows Qt platform plugin')


def prepare(image):
    """Move staged DLLs into a fresh jpackage image and update its launcher.

    Validate all expected inputs and destination collisions before modifying
    files. Retain plugins, bundled Java, and attribution. The release flow must
    smoke-test the resulting image before building its MSI.
    """
    configuration_path = image / 'app/OSCAR-UI.cfg'
    configuration = configuration_path.read_bytes()
    if configuration.count(OLD_OPTION) != 1 or NEW_OPTION in configuration:
        raise RuntimeError('Unexpected launcher library path; refusing to rewrite')
    source = image / 'app/qt/lib'
    for name in QT_LIBRARIES:
        if not (source / name).is_file():
            raise RuntimeError(f'Missing staged Qt library: {name}')
    if not (image / 'app/qt/plugins/platforms/qwindows.dll').is_file():
        raise RuntimeError('Missing Windows Qt platform plugin')
    libraries = list(source.glob('*.dll'))
    for library in libraries:
        if (image / library.name).exists():
            raise RuntimeError(f'Library destination already exists: {library.name}')
    for library in libraries:
        library.rename(image / library.name)
    configuration_path.write_bytes(configuration.replace(OLD_OPTION, NEW_OPTION))
    verify(image)
    print(f'[Windows DLLs] Moved {len(libraries)} libraries beside the launcher; no duplicate copies.')


def main():
    """Prepare an image, or verify an installed copy, using an explicit path."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--image', required=True, type=Path)
    parser.add_argument('--verify-only', action='store_true')
    args = parser.parse_args()
    if args.verify_only:
        verify(args.image)
        print('[Windows DLLs] Single-copy layout verified.')
    else:
        prepare(args.image)


if __name__ == '__main__':
    main()
