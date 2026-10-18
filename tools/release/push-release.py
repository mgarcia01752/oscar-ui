#!/usr/bin/env python3
"""Bump the UI version, commit, tag, and atomically push a release to origin."""
import argparse
from pathlib import Path
import re
import shlex
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
VERSION_PATTERN = r'(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)'


def git(*args, check=True):
    result = subprocess.run(['git', '-C', str(ROOT), *args], capture_output=True, text=True)
    if check and result.returncode:
        raise RuntimeError(result.stderr.strip() or f'git {args[0]} failed')
    return result


def release(bump, explicit, dry_run):
    if Path(git('rev-parse', '--show-toplevel').stdout.strip()).resolve() != ROOT:
        raise RuntimeError('Run the helper from its oscar-ui repository.')
    if git('status', '--porcelain').stdout:
        raise RuntimeError('Commit or stash pending changes before releasing; nothing was changed.')
    branch = git('symbolic-ref', '--quiet', '--short', 'HEAD').stdout.strip()
    pom = ROOT / 'pom.xml'
    text = pom.read_text()
    current = ET.fromstring(text).find('{http://maven.apache.org/POM/4.0.0}version').text
    if not re.fullmatch(VERSION_PATTERN, current):
        raise RuntimeError('pom.xml must have a stable MAJOR.MINOR.PATCH version.')
    numbers = [int(n) for n in current.split('.')]
    if explicit:
        if not re.fullmatch(VERSION_PATTERN, explicit):
            raise RuntimeError('--version must use MAJOR.MINOR.PATCH without a v prefix.')
        version = explicit
    else:
        index = {'major': 0, 'minor': 1, 'patch': 2}[bump]
        numbers[index] += 1
        numbers[index + 1:] = [0] * (2 - index)
        version = '.'.join(map(str, numbers))
    if tuple(map(int, version.split('.'))) <= tuple(map(int, current.split('.'))):
        raise RuntimeError('The release version must be newer than pom.xml.')
    tag = 'v' + version
    if git('show-ref', '--verify', '--quiet', f'refs/tags/{tag}', check=False).returncode == 0:
        raise RuntimeError(f'Tag {tag} already exists locally; never move a release tag.')
    if git('ls-remote', '--refs', 'origin', f'refs/tags/{tag}').stdout:
        raise RuntimeError(f'Tag {tag} already exists on origin; choose a new version.')
    git('fetch', '--no-tags', 'origin', f'refs/heads/{branch}')
    if git('merge-base', '--is-ancestor', 'FETCH_HEAD', 'HEAD', check=False).returncode:
        raise RuntimeError('The current branch is behind or diverged from origin; reconcile it first.')
    push = ['push', '--atomic', 'origin', f'HEAD:refs/heads/{branch}', f'refs/tags/{tag}:refs/tags/{tag}']
    print(f'Oscar Desktop {current} -> {version}; branch {branch}; tag {tag}')
    if dry_run:
        print('Dry run: no version edit, commit, tag, or push performed.')
        return
    git('var', 'GIT_AUTHOR_IDENT')
    git('var', 'GIT_COMMITTER_IDENT')
    # The root project version precedes dependencies; preserve the existing XML layout.
    updated, count = re.subn(r'(<version>\s*)' + re.escape(current) + r'(\s*</version>)',
                            lambda match: match[1] + version + match[2], text, count=1)
    if count != 1:
        raise RuntimeError('Cannot locate the project version in pom.xml.')
    pom.write_text(updated)
    git('add', '--', 'pom.xml')
    git('commit', '-m', f'Feature: Release Oscar Desktop {tag}')
    git('tag', '-a', tag, '-m', f'Oscar Desktop {version}')
    try:
        git(*push)
    except RuntimeError as error:
        raise RuntimeError(f'{error}\nLocal release commit and tag are retained. Retry without bumping:\n'
                           + shlex.join(['git', *push])) from error
    print(f'Pushed {tag}. Ubuntu and Windows workflows will attach their packages to one draft release.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group()
    parser.set_defaults(bump='patch')
    group.add_argument('--push-major', dest='bump', action='store_const', const='major',
                       help='Increment major and reset minor/patch')
    group.add_argument('--push-minor', dest='bump', action='store_const', const='minor',
                       help='Increment minor and reset patch')
    group.add_argument('--push-patch', dest='bump', action='store_const', const='patch',
                       help='Increment patch (default)')
    group.add_argument('--version', help='Explicit newer version, e.g. 0.2.0')
    parser.add_argument('--dry-run', action='store_true', help='Check origin and preview without editing or pushing')
    args = parser.parse_args()
    try:
        release(args.bump, args.version, args.dry_run)
    except (RuntimeError, OSError, ET.ParseError) as error:
        parser.exit(1, f'ERROR: {error}\n')


if __name__ == '__main__':
    main()
