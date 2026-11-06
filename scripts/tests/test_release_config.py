# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
"""Keep production release endpoints/templates in one packaged source."""
import importlib.util
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('release_settings_test', ROOT / 'scripts/build/release_config.py')
CONFIG = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CONFIG)


class ReleaseConfigTest(unittest.TestCase):
    def test_configuration_changes_propagate_without_code_defaults(self):
        """Alternate synthetic destinations and prefixes must override every default."""
        with tempfile.TemporaryDirectory() as temporary:
            source = Path(temporary) / 'release.properties'
            source.write_text('github.web=https://example.invalid\ngithub.api=https://api.example.invalid/repos\n'
                              'ui.repository=example/widget\ninstaller.template=widget-{version}-{os}-{arch}.{extension}\n',
                              encoding='utf-8')
            with patch.object(CONFIG, 'CONFIG', source):
                self.assertEqual('https://example.invalid/example/widget', CONFIG.project())
                self.assertEqual('https://api.example.invalid/repos/example/widget', CONFIG.project('ui', 'api'))
                self.assertEqual('widget-2.3.4-macos-x64.dmg', CONFIG.installer('2.3.4', 'macos', 'x64', 'dmg'))
            source.unlink()
            with patch.object(CONFIG, 'CONFIG', source), self.assertRaises(FileNotFoundError):
                CONFIG.project()

    def test_cli_works_outside_checkout(self):
        """Shell callers resolve configuration relative to the helper, not cwd."""
        with tempfile.TemporaryDirectory() as temporary:
            output = subprocess.check_output([sys.executable, str(ROOT / 'scripts/build/release_config.py'),
                                              'installer', '1.2.3', 'windows', 'x64', 'msi'],
                                             cwd=temporary, text=True, encoding='utf-8')
            self.assertEqual('oscar-ui-1.2.3-windows-x64.msi', output.strip())

    def test_production_code_does_not_duplicate_project_identity_or_templates(self):
        """Test fixtures and documentation may spell out expected/legacy names."""
        values = CONFIG.settings()
        forbidden = [values['ui.repository'], values['oscar.repository'], 'oscar-ui-{version}-',
                     'oscar-ui-${version}-', 'oscar-ui-$Version-', 'oscar-ui-" + version +']
        for directory in ('src/main/java', 'scripts', 'tools', '.github/workflows'):
            for source in (ROOT / directory).rglob('*'):
                if not source.is_file() or source.suffix not in ('.java', '.py', '.sh', '.ps1', '.yml'):
                    continue
                if 'tests' in source.parts or source.name.startswith(('test_', 'test-')):
                    continue
                text = source.read_text(encoding='utf-8')
                # Attribution tables intentionally name the upstream copyright owner/project.
                text = text.replace('Comcast/Oscar {', 'upstream {')
                for literal in forbidden:
                    self.assertNotIn(literal, text, str(source.relative_to(ROOT)))
