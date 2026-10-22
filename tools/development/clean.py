#!/usr/bin/env python3
# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
"""Remove generated build and installer output, preserving dependency caches."""
import argparse
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
OUTPUT_DIRECTORIES = ('target', 'dist')


def main():
    """Validate the complete cleanup plan, then preview or remove selected output directories."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--dry-run', action='store_true', help='list directories without deleting anything')
    parser.add_argument('--target', action='store_true', help='clean Maven and application build output only')
    parser.add_argument('--dist', action='store_true', help='clean generated installer images only')
    args = parser.parse_args()
    selected = [name for name in OUTPUT_DIRECTORIES if getattr(args, name)] or list(OUTPUT_DIRECTORIES)
    try:
        # Refuse ambiguous paths and tracked content before deleting any output.
        tracked = subprocess.check_output(
            ['git', '-C', str(ROOT), 'ls-files', '-z', '--', *selected])
        if tracked:
            raise ValueError('Selected output contains tracked files; cleanup refused.')
        paths = [ROOT / name for name in selected]
        for path in paths:
            if path.is_symlink():
                raise ValueError(f'Refusing symlink output directory: {path}')
            if path.exists() and not path.is_dir():
                raise ValueError(f'Expected an output directory: {path}')
        for path in paths:
            if not path.exists():
                print(f'Already absent: {path}')
            elif args.dry_run:
                print(f'Would remove: {path}')
            else:
                shutil.rmtree(path)
                print(f'Removed: {path}')
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        print(f'[clean] ERROR: {error}', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
