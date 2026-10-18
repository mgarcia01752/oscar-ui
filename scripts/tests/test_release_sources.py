# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
import hashlib
import importlib.util
import io
from pathlib import Path
import tempfile
import sys
from types import SimpleNamespace
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('sources', Path(__file__).resolve().parents[1] / 'build/collect-release-sources.py')
sources = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sources)


class ReleaseSourcesTest(unittest.TestCase):
    def test_bundles_matching_sources_and_checksums_per_platform(self):
        """Verify platform bundles without changing the host Python platform.

        Keep standard-library executable lookup on the real host platform
        while simulating the collector's Windows and Linux source selection.
        """
        host_platform = sys.platform
        for platform, count in [('win32', 3), ('linux', 4)]:
            with self.subTest(platform=platform), tempfile.TemporaryDirectory() as temp:
                root = Path(temp)
                (root / 'target/app').mkdir(parents=True)
                (root / 'jdk').mkdir()
                (root / 'jdk/release').write_text('IMPLEMENTOR="Eclipse Adoptium"\nJAVA_RUNTIME_VERSION="21.0.10+7-LTS"\n')
                content = b'example source archive'
                with patch.object(sources, 'JAVA_SOURCE_SHA256', hashlib.sha256(content).hexdigest()), patch.object(sources, 'ROOT', root), patch.object(sources, 'sys', SimpleNamespace(platform=platform)), patch.dict(sources.os.environ, {'JAVA_HOME': str(root / 'jdk')}), patch.object(sources.urllib.request, 'urlopen', side_effect=lambda *a, **k: io.BytesIO(content)):
                    self.assertEqual(host_platform, sys.platform)
                    sources.collect()
                manifest = (root / 'target/app/sources/SHA256SUMS.txt').read_text().splitlines()
                self.assertEqual(count, len(manifest))
                for line in manifest:
                    digest, name = line.split('  ')
                    self.assertEqual(hashlib.sha256(content).hexdigest(), digest)
                    self.assertEqual(content, (root / 'target/app/sources' / name).read_bytes())
                self.assertFalse(list(root.rglob('*.download')))

    def test_rejects_corrupted_java_source(self):
        """Block packaging when downloaded JDK sources fail the vendor pin.

        Ensure invalid archive content is removed and no manifest is emitted.
        """
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / 'target/app').mkdir(parents=True)
            (root / 'jdk').mkdir()
            (root / 'jdk/release').write_text(
                'IMPLEMENTOR="Eclipse Adoptium"\nJAVA_RUNTIME_VERSION="21.0.10+7-LTS"\n', encoding='utf-8')
            with patch.object(sources, 'ROOT', root), patch.dict(sources.os.environ, {'JAVA_HOME': str(root / 'jdk')}), patch.object(sources.urllib.request, 'urlopen', side_effect=lambda *a, **k: io.BytesIO(b'corrupted')):
                with self.assertRaisesRegex(RuntimeError, 'vendor checksum'):
                    sources.collect()
            self.assertFalse(list((root / 'target/app/sources').glob('OpenJDK*')))
            self.assertFalse((root / 'target/app/sources/SHA256SUMS.txt').exists())

    def test_rejects_unmatched_jdk_before_downloading(self):
        with tempfile.TemporaryDirectory() as temp:
            home = Path(temp)
            (home / 'release').write_text('IMPLEMENTOR="Ubuntu"\nJAVA_RUNTIME_VERSION="21.0.10+7"\n')
            with patch.dict(sources.os.environ, {'JAVA_HOME': temp}), patch.object(sources.urllib.request, 'urlopen') as request:
                with self.assertRaisesRegex(RuntimeError, 'requires Temurin'):
                    sources.collect()
                request.assert_not_called()


if __name__ == '__main__':
    unittest.main()
