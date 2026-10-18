"""Exercise dependency updates without network access or a Maven installation."""
import contextlib
import hashlib
import importlib.util
import io
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

REPO = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('update_oscar', REPO / 'tools/development/update-oscar.py')
UPDATER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(UPDATER)
FILES = ('pom.xml', 'scripts/unix/bootstrap-oscar.sh', 'scripts/windows/bootstrap-oscar.ps1',
         'scripts/shared/desktop.py', 'THIRD-PARTY-NOTICES.md', 'docs/development/README.md',
         'docs/development/ui-architecture.md')


class UpdateOscarTest(unittest.TestCase):
    """Verify reference integrity, release validation, and failure recovery."""

    def setUp(self):
        """Create a disposable checkout and a valid synthetic OSCAR release."""
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        for name in FILES:
            target = self.root / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes((REPO / name).read_bytes())
        self.originals = {name: (self.root / name).read_bytes() for name in FILES}
        current = UPDATER.ET.parse(self.root / 'pom.xml').findtext(
            'm:properties/m:oscar.version', namespaces=UPDATER.POM_NAMESPACE)
        major, minor, patch_number = map(int, current.split('.'))
        self.version = f'{major}.{minor}.{patch_number + 1}'
        output = io.BytesIO()
        with zipfile.ZipFile(output, 'w') as archive:
            archive.writestr('META-INF/maven/com.comcast.oscar/oscar/pom.xml',
                             '<project xmlns="http://maven.apache.org/POM/4.0.0">'
                             '<groupId>com.comcast.oscar</groupId><artifactId>oscar</artifactId>'
                             f'<version>{self.version}</version></project>')
        self.jar = output.getvalue()
        self.digest = hashlib.sha256(self.jar).hexdigest()
        self.asset = {'name': 'oscar-thin.jar', 'size': len(self.jar), 'state': 'uploaded',
                      'browser_download_url': 'https://example.invalid/jar',
                      'digest': f'sha256:{self.digest}'}
        self.release = {'tag_name': f'v{self.version}', 'draft': False, 'prerelease': False,
                        'assets': [self.asset]}
        self.commit = 'a' * 40

    def test_dry_run_has_no_local_mutations(self):
        """Preview fully verifies the download without installing or writing pins."""
        before = set(self.root.rglob('*'))
        with patch.object(UPDATER, '_resolve', return_value=(self.release, self.asset, self.commit)), \
                patch.object(UPDATER, '_fetch', side_effect=lambda url: self.jar if url.endswith('/jar') else b'Notice'), \
                patch.object(UPDATER, '_maven') as maven, contextlib.redirect_stdout(io.StringIO()) as output:
            UPDATER._update(self.root, self.version, True)
        maven.assert_not_called()
        self.assertEqual(set(self.root.rglob('*')), before)
        self.assertIn('Dry run complete', output.getvalue())
        self.assertEqual(self.originals, {name: (self.root / name).read_bytes() for name in FILES})

    def test_success_installs_then_updates_and_builds(self):
        """Both bootstrap platforms and packaging select the verified artifact."""
        with patch.object(UPDATER, '_resolve', return_value=(self.release, self.asset, self.commit)), \
                patch.object(UPDATER, '_fetch', side_effect=lambda url: self.jar if url.endswith('/jar') else b'Notice'), \
                patch.object(UPDATER, '_maven') as maven, contextlib.redirect_stdout(io.StringIO()):
            UPDATER._update(self.root, self.version, False)
        self.assertEqual(maven.call_count, 2)
        self.assertIn('install-file', maven.call_args_list[0].args[1])
        self.assertEqual(maven.call_args_list[1].args[1:], ('clean', 'verify'))
        for name in FILES:
            self.assertIn(self.version, (self.root / name).read_text())
        for name in ('scripts/unix/bootstrap-oscar.sh', 'scripts/windows/bootstrap-oscar.ps1'):
            self.assertIn(self.digest, (self.root / name).read_text())
            self.assertIn(self.commit, (self.root / name).read_text())
        self.assertEqual((self.root / f'.tools/oscar-{self.version}/oscar-thin.jar').read_bytes(), self.jar)
        self.assertIn('Oscar 3.2.0 adds', (self.root / 'docs/development/README.md').read_text())

    def test_build_failure_restores_references(self):
        """An incompatible dependency cannot leave tracked references half updated."""
        with patch.object(UPDATER, '_resolve', return_value=(self.release, self.asset, self.commit)), \
                patch.object(UPDATER, '_fetch', side_effect=lambda url: self.jar if url.endswith('/jar') else b'Notice'), \
                patch.object(UPDATER, '_maven', side_effect=[None, subprocess.CalledProcessError(1, 'maven')]), \
                contextlib.redirect_stdout(io.StringIO()):
            with self.assertRaises(subprocess.CalledProcessError):
                UPDATER._update(self.root, self.version, False)
        self.assertEqual(self.originals, {name: (self.root / name).read_bytes() for name in FILES})

    def test_install_failure_leaves_references_unchanged(self):
        """A failed local Maven installation never changes source references."""
        with patch.object(UPDATER, '_resolve', return_value=(self.release, self.asset, self.commit)), \
                patch.object(UPDATER, '_fetch', side_effect=lambda url: self.jar if url.endswith('/jar') else b'Notice'), \
                patch.object(UPDATER, '_maven', side_effect=subprocess.CalledProcessError(1, 'maven')), \
                contextlib.redirect_stdout(io.StringIO()):
            with self.assertRaises(subprocess.CalledProcessError):
                UPDATER._update(self.root, self.version, False)
        self.assertEqual(self.originals, {name: (self.root / name).read_bytes() for name in FILES})

    def test_checksum_mismatch_is_rejected(self):
        """Reject a JAR when GitHub's published digest differs."""
        with self.assertRaisesRegex(RuntimeError, 'SHA-256'):
            UPDATER._verify_checksum(self.release, self.asset, 'b' * 64)

    def test_checksum_manifest_is_verified(self):
        """Validate a conventional checksum manifest even without an asset digest."""
        asset = dict(self.asset, digest=None)
        release = {'assets': [asset, {'name': 'SHA256SUMS.txt', 'browser_download_url': 'https://example.invalid/sums'}]}
        with patch.object(UPDATER, '_fetch', return_value=f'{self.digest}  oscar-thin.jar\n'.encode()), \
                contextlib.redirect_stdout(io.StringIO()):
            UPDATER._verify_checksum(release, asset, self.digest)
            with self.assertRaises(RuntimeError):
                UPDATER._verify_checksum(release, asset, 'b' * 64)

    def test_wrong_embedded_version_is_rejected(self):
        """The download must contain the requested Maven artifact version."""
        path = self.root / 'wrong.jar'
        path.write_bytes(self.jar)
        with self.assertRaisesRegex(RuntimeError, 'version'):
            UPDATER._verify_jar(path, '0.0.1')

    def test_missing_asset_and_unpublished_release_are_rejected(self):
        """Only a published release with the thin JAR can be selected."""
        for release in (dict(self.release, assets=[]), dict(self.release, draft=True),
                        dict(self.release, prerelease=True)):
            with self.subTest(release=release), patch.object(UPDATER, '_api', return_value=release):
                with self.assertRaises(RuntimeError):
                    UPDATER._resolve(self.version)

    def test_annotated_tag_is_resolved(self):
        """Resolve annotated tags to the commit used for attribution downloads."""
        with patch.object(UPDATER, '_api', side_effect=[self.release,
                          {'object': {'type': 'tag', 'sha': 'b' * 40}},
                          {'object': {'type': 'commit', 'sha': self.commit}}]):
            self.assertEqual(UPDATER._resolve(self.version)[2], self.commit)

    def test_failed_download_does_not_install_or_update(self):
        """Network failures leave the checkout untouched."""
        with patch.object(UPDATER, '_resolve', return_value=(self.release, self.asset, self.commit)), \
                patch.object(UPDATER, '_fetch', side_effect=OSError('network failure')), \
                patch.object(UPDATER, '_maven') as maven, contextlib.redirect_stdout(io.StringIO()):
            with self.assertRaises(OSError):
                UPDATER._update(self.root, self.version, False)
        maven.assert_not_called()
        self.assertEqual(self.originals, {name: (self.root / name).read_bytes() for name in FILES})

    def test_unknown_reference_layout_fails_before_writing(self):
        """Unexpected source layout requires review rather than a partial replacement."""
        (self.root / 'scripts/shared/desktop.py').write_text('changed layout')
        with self.assertRaisesRegex(RuntimeError, 'Expected one reference'):
            UPDATER._references(self.root, self.version, self.commit, self.digest)
        self.assertEqual((self.root / 'pom.xml').read_bytes(), self.originals['pom.xml'])

    def test_conflicting_cache_is_preserved(self):
        """Never replace a different existing release cache or install its replacement."""
        cache = self.root / f'.tools/oscar-{self.version}'
        cache.mkdir(parents=True)
        (cache / 'oscar-thin.jar').write_bytes(b'existing artifact')
        with patch.object(UPDATER, '_resolve', return_value=(self.release, self.asset, self.commit)), \
                patch.object(UPDATER, '_fetch', side_effect=lambda url: self.jar if url.endswith('/jar') else b'Notice'), \
                patch.object(UPDATER, '_maven') as maven, contextlib.redirect_stdout(io.StringIO()):
            with self.assertRaisesRegex(RuntimeError, 'cache differs'):
                UPDATER._update(self.root, self.version, False)
        maven.assert_not_called()
        self.assertEqual((cache / 'oscar-thin.jar').read_bytes(), b'existing artifact')
        self.assertEqual(self.originals, {name: (self.root / name).read_bytes() for name in FILES})

    def test_no_published_checksum_is_reported(self):
        """Do not claim independent checksum verification when no digest is published."""
        asset = dict(self.asset, digest=None)
        with contextlib.redirect_stdout(io.StringIO()) as output:
            UPDATER._verify_checksum({'assets': [asset]}, asset, self.digest)
        self.assertIn('No published checksum found', output.getvalue())

    def test_invalid_version_is_rejected_without_network(self):
        """Reject malformed version strings before constructing remote URLs."""
        with patch.object(UPDATER, '_resolve') as resolve:
            for version in ('v3.2.1', '../3.2.1', '3.02.1', '3.2.1-SNAPSHOT'):
                with self.assertRaises(RuntimeError):
                    UPDATER._update(self.root, version, False)
        resolve.assert_not_called()


if __name__ == '__main__':
    unittest.main()
