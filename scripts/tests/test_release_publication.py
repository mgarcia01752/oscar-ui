# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
"""Exercise the publication shell with a draft-only GitHub API fixture."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import textwrap
import unittest

ROOT = Path(__file__).resolve().parents[2]


@unittest.skipUnless(os.name == 'posix' and shutil.which('bash') and shutil.which('jq'),
                     'Publication runs on Linux and requires bash/jq')
class ReleasePublicationTest(unittest.TestCase):
    """Run the workflow's actual shell without changing a GitHub release."""

    def test_draft_is_resolved_by_id_before_publication(self):
        """A draft missing from the tag endpoint can pass all asset gates."""
        self.run_publication('42', True)

    def test_missing_or_ambiguous_release_cannot_publish(self):
        """Require one numeric release identity before downloading or publishing."""
        for identity in ('', '42\n43'):
            with self.subTest(identity=identity):
                self.run_publication(identity, False)

    def test_missing_macos_image_blocks_publication(self):
        """A successful Windows metadata check cannot publish without the DMG."""
        for architecture in ('arm64', 'x64'):
            with self.subTest(missing_architecture=architecture):
                remaining = tuple(value for value in ('arm64', 'x64') if value != architecture)
                self.run_publication('42', False, macos_images=remaining)

    def test_wrong_or_duplicate_installer_names_block_publication(self):
        """Reject mixed case, wrong version/architecture and duplicate assets."""
        for name in ('OSCAR-UI-1.0.0-ubuntu-x64.deb', 'oscar-ui-2.0.0-ubuntu-x64.deb',
                     'oscar-ui-1.0.0-ubuntu-arm64.deb', 'oscar-ui_amd64.deb'):
            with self.subTest(name=name):
                self.run_publication('42', False, wrong_name=name)
        self.run_publication('42', False, duplicate=True)

    def run_publication(self, identity, success, macos_images=('arm64', 'x64'), wrong_name=None, duplicate=False):
        """Execute the workflow using isolated assets and a non-network gh command.

        Decode captured output as UTF-8 independently of Python or host defaults.
        """
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / 'bin').mkdir()
            (root / 'macos-release').mkdir()
            for architecture in macos_images:
                (root / f'macos-release/oscar-ui-1.0.0-macos-{architecture}.dmg').write_bytes(b'test dmg')
            (root / 'scripts/build').mkdir(parents=True)
            for helper in ('verify-windows-release.py', 'release_config.py'):
                shutil.copy2(ROOT / 'scripts/build' / helper, root / 'scripts/build')
            (root / 'src/main/resources').mkdir(parents=True)
            shutil.copy2(ROOT / 'src/main/resources/release.properties', root / 'src/main/resources')
            assets = [{'name': name, 'size': 10, 'state': 'uploaded'} for name in
                      ['oscar-ui-1.0.0-windows-x64.msi', 'oscar-ui-1.0.0-ubuntu-x64.deb',
                       'windows-patch-inputs.zip', 'windows-update.json']]
            if wrong_name:
                assets[1]['name'] = wrong_name
            if duplicate:
                assets.append(dict(assets[1]))
            (root / 'assets.json').write_text(json.dumps({'assets': assets}), encoding='utf-8')
            (root / 'identity').write_text(identity, encoding='utf-8')
            gh = root / 'bin/gh'
            gh.write_text('''#!/usr/bin/env python3
import json
from pathlib import Path
import sys
args = sys.argv[1:]
if args[:2] == ['api', '--paginate']:
    print(Path('identity').read_text(encoding='utf-8'))
elif args == ['api', 'repos/example/project/releases/42']:
    print(Path('assets.json').read_text(encoding='utf-8'))
elif args[:2] == ['release', 'download']:
    Path('windows-metadata').mkdir()
    Path('windows-metadata/windows-update.json').write_text(json.dumps(
        {'layout': 1, 'version': '1.0.0', 'fromVersion': None, 'patch': None}), encoding='utf-8')
elif args[:2] == ['release', 'upload']:
    assets = [Path(value) for value in args[3:] if not value.startswith('--')]
    if len(assets) != 2 or not all(asset.is_file() for asset in assets):
        sys.exit('Expected both macOS DMGs before uploading')
    Path('uploaded.json').write_text(json.dumps([asset.name for asset in assets]), encoding='utf-8')
elif args == ['release', 'edit', 'v1.0.0', '--draft=false']:
    Path('published').touch()
else:
    sys.exit('Unsupported API request (draft tag lookups return 404): ' + repr(args))
''', encoding='utf-8')
            gh.chmod(0o755)
            workflow = (ROOT / '.github/workflows/release.yml').read_text(encoding='utf-8')
            publication = workflow.split('      - name: Verify installers and publish\n', 1)[1]
            script = textwrap.dedent(publication.split('        run: |\n', 1)[1])
            environment = dict(os.environ, PATH=str(root / 'bin') + os.pathsep + os.environ['PATH'],
                               GH_REPO='example/project', RELEASE_TAG='v1.0.0')
            result = subprocess.run(['bash', '-c', script], cwd=root, env=environment,
                                    capture_output=True, text=True, encoding='utf-8')
            self.assertEqual(success, result.returncode == 0, result.stdout + result.stderr)
            self.assertEqual(success, (root / 'published').exists())
            if success:
                self.assertEqual(['oscar-ui-1.0.0-macos-arm64.dmg', 'oscar-ui-1.0.0-macos-x64.dmg'],
                                 json.loads((root / 'uploaded.json').read_text(encoding='utf-8')))
            else:
                self.assertFalse((root / 'uploaded.json').exists())
