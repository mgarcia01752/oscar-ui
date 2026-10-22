# Update the OSCAR dependency

[Development guide](../README.md) · [Release packaging](packaging.md)

Publish the desired stable OSCAR release first. Its GitHub release must include
`oscar-thin.jar` with the requested version in its embedded Maven POM.
Unreleased source changes in a local OSCAR checkout are not used.

## Preview and update

Use Python 3 and Java 21. Run from the OSCAR-UI checkout; the script resolves
project paths from its own location. No GitHub CLI is required. Public GitHub
API rate limits apply; an unavailable release or failed request stops the update.

To use the release designated latest by GitHub:

```bash
python3 tools/development/update-oscar.py --latest-jar --dry-run
python3 tools/development/update-oscar.py --latest-jar
```

Choose exactly one of `--latest-jar` and `--version`. Latest selection requires
a published stable `vMAJOR.MINOR.PATCH` tag and uses the same asset checks,
downgrade protection, Maven verification, and rollback as explicit selection.
It does not fall back to an older release if the latest release's JAR is missing.

To select a specific published version, for example:

```bash
python3 tools/development/update-oscar.py --version 3.2.1 --dry-run
python3 tools/development/update-oscar.py --version 3.2.1
```

On Windows, use `python` instead of `python3` if that is your Python command.
The project Maven wrapper selects `mvnw.cmd` on Windows and `mvnw` elsewhere.

Progress messages show release resolution, commit, download size, SHA-256
verification, attribution downloads, each reference update, Maven installation,
and build/test status. Maven output is streamed to the terminal. A nonzero exit
status means the update did not complete successfully.

Dry-run downloads and verifies into temporary storage, previews the affected
files, and removes the temporary downloads. It does not modify project files,
the OSCAR cache, or the local Maven repository.

## What changes

The helper resolves annotated or lightweight release tags, validates the JAR
archive and its embedded Maven coordinates, and calculates SHA-256. It compares
against the release asset digest and supported published SHA-256 manifests when
available. When neither exists, it explicitly reports that the new pin is based
on the HTTPS download; this is not independent authenticity verification.

It updates these active references together:

- `pom.xml`: OSCAR dependency version.
- `scripts/unix/bootstrap-oscar.sh` and
  `scripts/windows/bootstrap-oscar.ps1`: version, commit, and checksum.
- `scripts/build/desktop.py`: attribution cache directory.
- `THIRD-PARTY-NOTICES.md`: selected release and commit.
- Development README and UI architecture: current dependency references.

Historical statements about when a feature or fix was introduced remain
unchanged. Unexpected reference layouts stop the update for review. Downgrades
and changing pins for the currently selected version are rejected.

The verified JAR is installed through the same Maven install-file plugin used
by bootstrap, with its embedded POM, into the project's `.m2` repository.
All three entry points resolve the Install plugin version from `pom.xml`.
The JAR, LICENSE, and NOTICE are cached under the versioned `.tools/oscar-*`
directory. Previous versions are retained. Conflicting cached release files are
not overwritten. The helper does not download the JAR a second time to install it.

## Validation and failure handling

After updating references, the helper runs Maven `clean verify`. This rebuilds
the JAR and runs ordinary Java tests; it does not establish native Qt or Windows
installer compatibility. Rebuild and check the desktop image using the
[development guide](../README.md) before releasing.

If installation fails, tracked references remain unchanged. If reference
writing or Maven verification fails, their original contents are restored.
Downloaded Maven artifacts, the new versioned cache, and build output may remain;
the helper does not delete shared caches. Maven `clean` replaces existing build
output. API incompatibilities require a separate, reviewed code change.
Avoid editing the affected files concurrently while the updater runs.

Review `git diff` after success. The helper does not commit, tag, push, or publish.

## Test the helper

```bash
python3 -X warn_default_encoding -W error::EncodingWarning -m unittest discover -s tools/development -p 'test_*.py'
```

Tests use synthetic release metadata and JARs, mocked downloads/Maven calls,
and temporary checkouts. They do not update the real dependency or publish a
release. CI runs them on Ubuntu, macOS, and the Windows release runner.

CI treats implicit text-file encodings as errors in this suite. Repository
text is UTF-8, including when tests run on Windows with a CP1252 locale.
