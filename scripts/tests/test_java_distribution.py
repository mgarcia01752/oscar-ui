# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
"""Regression checks for the packaged Java redistribution materials."""
import hashlib
import importlib.util
import io
from pathlib import Path
import tarfile
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location(
    'java_distribution', Path(__file__).resolve().parents[1] / 'shared/verify-java-distribution.py')
CHECKER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CHECKER)


class JavaDistributionTest(unittest.TestCase):
    def test_accepts_both_package_layouts(self):
        """Check Linux and Windows layouts with intact source and notice files.

        Each fixture retains all notices from a separate build JDK directory.
        """
        for prefix in ('', 'lib'):
            with self.subTest(prefix=prefix), tempfile.TemporaryDirectory() as temporary:
                runtime, sources, jdk = self._fixture(Path(temporary) / prefix)
                CHECKER.verify(runtime, sources, jdk)

    def test_accepts_identical_shared_notice(self):
        """Accept a shared notice when packaging removes a duplicate module link.

        All original text must remain present in the runtime's legal directory.
        """
        with tempfile.TemporaryDirectory() as temporary:
            runtime, sources, jdk = self._fixture(Path(temporary))
            for home in (runtime, jdk):
                (home / 'legal/java.logging').mkdir()
            (jdk / 'legal/java.logging/LICENSE').write_bytes((jdk / 'legal/java.base/LICENSE').read_bytes())
            release = runtime / 'release'
            release.write_text(release.read_text(encoding='utf-8').replace('MODULES="java.base"', 'MODULES="java.base java.logging"'), encoding='utf-8')
            CHECKER.verify(runtime, sources, jdk)

    def test_rejects_missing_or_changed_materials(self):
        """Reject missing notices, changed copyrights, and source corruption.

        Rebuild each fixture to isolate the failing distribution obligation.
        """
        for damage in ('license', 'notice', 'changed', 'checksum', 'version', 'archive'):
            with self.subTest(damage=damage), tempfile.TemporaryDirectory() as temporary:
                runtime, sources, jdk = self._fixture(Path(temporary))
                if damage == 'license':
                    (runtime / 'legal/java.base/LICENSE').write_text('GPL only', encoding='utf-8')
                elif damage == 'notice':
                    (runtime / 'legal/java.base/third-party.md').unlink()
                elif damage == 'changed':
                    (runtime / 'legal/java.base/third-party.md').write_text('Changed copyright', encoding='utf-8')
                elif damage == 'checksum':
                    (sources / 'SHA256SUMS.txt').write_text('', encoding='utf-8')
                elif damage == 'version':
                    release = runtime / 'release'
                    release.write_text(release.read_text(encoding='utf-8').replace('21.0.10', '21.0.11'), encoding='utf-8')
                else:
                    self._archive(sources, include_build=False)
                with self.assertRaises((RuntimeError, FileNotFoundError)):
                    CHECKER.verify(runtime, sources, jdk)

    def _fixture(self, root):
        runtime, sources, jdk = root / 'runtime', root / 'app/sources', root / 'jdk'
        for home in (runtime, jdk):
            legal = home / 'legal/java.base'
            legal.mkdir(parents=True)
            (legal / 'LICENSE').write_text('GNU GENERAL PUBLIC LICENSE\nCLASSPATH', encoding='utf-8')
            (legal / 'third-party.md').write_text('Copyright original author', encoding='utf-8')
        (runtime / 'release').write_text(
            'JAVA_VERSION="21.0.10"\nMODULES="java.base"\n', encoding='utf-8')
        (jdk / 'release').write_text(
            'IMPLEMENTOR="Eclipse Adoptium"\nJAVA_RUNTIME_VERSION="21.0.10+7-LTS"\n', encoding='utf-8')
        sources.mkdir(parents=True)
        self._archive(sources)
        return runtime, sources, jdk

    def _archive(self, sources, include_build=True):
        archive = sources / 'OpenJDK21U-jdk-sources_21.0.10_7.tar.gz'
        names = ['LICENSE', 'ADDITIONAL_LICENSE_INFO', 'ASSEMBLY_EXCEPTION', 'configure',
                 'doc/building.md', 'src/java.base/share/classes/java/lang/Object.java']
        if include_build:
            names.append('make/Init.gmk')
        with tarfile.open(archive, 'w:gz') as output:
            for name in names:
                member = tarfile.TarInfo('jdk/' + name)
                member.size = 1
                output.addfile(member, io.BytesIO(b'x'))
        digest = hashlib.sha256(archive.read_bytes()).hexdigest()
        (sources / 'SHA256SUMS.txt').write_text(f'{digest}  {archive.name}\n', encoding='utf-8')


if __name__ == '__main__':
    unittest.main()
