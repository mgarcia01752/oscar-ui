# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
"""Read the shared packaged release configuration; also usable from shell workflows."""
import argparse
from pathlib import Path

CONFIG = Path(__file__).resolve().parents[2] / 'src/main/resources/release.properties'


def settings():
    """Return the canonical UTF-8 flat properties without environment overrides."""
    return dict(line.split('=', 1) for line in CONFIG.read_text(encoding='utf-8').splitlines()
                if line.strip() and not line.lstrip().startswith('#'))


def project(name='ui', endpoint='web'):
    """Return a project web, API or raw-content base URL from shared settings."""
    values = settings()
    return values['github.' + endpoint] + '/' + values[name + '.repository']


def installer(version, operating_system, architecture, extension):
    """Format a release installer basename using the shared naming template."""
    return settings()['installer.template'].format(version=version, os=operating_system,
                                                  arch=architecture, extension=extension)


def main():
    """Print a derived URL or basename for shell/CI callers; never mutate files."""
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    url = commands.add_parser('url')
    url.add_argument('project', choices=['ui', 'oscar'])
    url.add_argument('endpoint', choices=['web', 'api', 'raw'], nargs='?', default='web')
    name = commands.add_parser('installer')
    name.add_argument('version')
    name.add_argument('os', choices=['windows', 'ubuntu', 'macos'])
    name.add_argument('arch', choices=['x64', 'arm64'])
    name.add_argument('extension', choices=['msi', 'deb', 'dmg'])
    args = parser.parse_args()
    if args.command == 'url':
        print(project(args.project, args.endpoint))
    else:
        print(installer(args.version, args.os, args.arch, args.extension))


if __name__ == '__main__':
    main()
