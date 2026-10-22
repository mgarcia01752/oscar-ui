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
        self.run_publication('42', False, macos_image=False)

    def run_publication(self, identity, success, macos_image=True):
        """Execute the workflow using isolated assets and a non-network gh command."""
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / 'bin').mkdir()
            (root / 'macos-release').mkdir()
            if macos_image:
                (root / 'macos-release/OSCAR-UI-1.0.0-macos-arm64.dmg').write_bytes(b'test dmg')
            (root / 'scripts/build').mkdir(parents=True)
            shutil.copy2(ROOT / 'scripts/build/verify-windows-release.py', root / 'scripts/build')
            assets = [{'name': name, 'size': 10, 'state': 'uploaded'} for name in
                      ['OSCAR-UI-1.0.0-windows-x64.msi', 'oscar-ui_amd64.deb',
                       'windows-patch-inputs.zip', 'windows-update.json']]
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
    pass
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
                                    capture_output=True, text=True)
            self.assertEqual(success, result.returncode == 0, result.stdout + result.stderr)
            self.assertEqual(success, (root / 'published').exists())
