# Development

[Back to OSCAR-UI](../../README.md)

This guide covers building from source, testing, and current implementation
behavior. For ready-to-run downloads, use the installation instructions in the
[README](../../README.md).

Run development commands from the repository root. Use Java 21 and the
project-local bootstrap tools; no machine-specific checkout location is required.
Keep personal configuration files in the ignored `test-config/` directory.

- [Deferred work and follow-up checks](TODO.md)
- [UI architecture](ui-architecture.md)
- [Verification checklist](checklist.md)
- [Local pre-push checks](pre-push-checks.md)
- [Release packaging and version tags](packaging.md)
- [Build script layout and commands](../../scripts/README.md)
- [Agent instructions](../../AGENTS.md)
- [Coding style](../../skills/coding-style/SKILL.md)

## Development setup

On Debian/Ubuntu Linux x86_64:

```sh
./install.sh --development --check
./install.sh --development
```

If system packages are missing, the installer prints an `apt-get` command.
Run that command yourself, then rerun setup. Java 21 must be selected through
`JAVA_HOME` or `PATH`; setup does not change your shell configuration or use
elevated privileges. Other Linux distributions are not currently supported by
this installer.

Development setup downloads the verified Oscar release, installs Qt 6.8.3
through a project-local Python virtual environment, and builds and tests the
UI using the Maven wrapper. Existing Qt installations can be selected with
`QT_HOME`. Downloads require internet access. The `--check` option checks only
system prerequisites, without downloading or building anything.

After a successful setup, launch with `./scripts/unix/desktop.sh run`. A graphical
desktop is required for normal use. Prerequisite checks also cover Git, `unzip`,
Pillow (`python3-pil`) for icon conversion, `dpkg-dev` and `fakeroot` for DEB
packaging, and `xvfb`/`xauth` for headless X11 verification. Maven is downloaded
by the wrapper; Qt's `aqtinstall` is installed in `.tools/venv`. No system Maven,
Qt development packages, or separate Python pip installation is needed.

Normal development accepts any Java 21 JDK. Release source bundling additionally
requires **Temurin 21.0.10+7**, as described in [packaging](packaging.md);
passing development preflight does not verify that release-specific pin.
RPM packaging and Windows build tools are outside this Linux setup.

Validate installer changes without installing packages or downloading tools:

```sh
bash scripts/tests/test-install.sh
```

### Run the application

Run commands from the repository root in a terminal on your graphical desktop:

```bash
./scripts/unix/desktop.sh build
./scripts/unix/desktop.sh run
```

For a VM, open the terminal inside the VM's graphical desktop so the application
uses that session's display. Keep the terminal open while using the application;
Ctrl+C stops it. Rebuild after Java source changes because `run` uses the existing
build. Remote GUI sessions require their own display setup.

## Oscar dependency

To upgrade the pinned release, use the [OSCAR dependency updater](oscar-dependency-updates.md).
It downloads the JAR, updates Linux/Windows pins and current-version references,
installs locally, and runs Maven verification with progress output.

Until Oscar is published to Maven Central, use the prebuilt thin JAR from
[Oscar v3.2.0](https://github.com/Comcast/Oscar/releases/tag/v3.2.0).
Bootstrap verifies its pinned SHA-256 and installs the JAR with its embedded
Maven POM into this project's `.m2` cache. Dependencies still come from Maven
Central; no Oscar source checkout or source build is required.
Oscar 3.2.0 adds a shared-secret MIC verification API and D3.1 extended MIC
generation, shared with D4.0. Tools → Check Shared Secret uses the verification API for a selected binary
without altering the open document.
It retains the 123 SNMP security/community OID mappings for USM, VACM,
DH key-change, and community objects. Indexed names still need Net-SNMP
unless cached; this update does not change the numeric-OID display policy.
It retains the corrected classifier-error names from 3.1.1:
ErroredParameter, ErrorCode, and ErrorMessage, so those sub-TLVs can be edited. Older text templates with
spaced spellings must update those names or be regenerated.

Use Java 21. On Linux/macOS, bootstrap also requires `curl` and either `sha256sum`
or `shasum` (available on macOS):

```sh
scripts/unix/bootstrap-oscar.sh
./mvnw -B -Dmaven.repo.local="$PWD/.m2" test
```

On Windows, run from PowerShell:

```powershell
./scripts/windows/bootstrap-oscar.ps1
./mvnw.cmd -B "-Dmaven.repo.local=$PWD/.m2" test
```

The desktop build command (`scripts/unix/desktop.sh build` or
`scripts/windows/desktop.ps1 build`) uses the same cache. Bootstrap also downloads the
release's original license and notice for inclusion in desktop packages.

## Saving Repository Changes

Enable [local pre-push checks](pre-push-checks.md) once per checkout with
`python3 tools/git/install-hooks.py`. The hook covers branch and tag pushes;
`python3 tools/development/pre-push-checks.py` runs the same gate before committing.


From the oscar-ui repository, use its local helper:

```bash
tools/git/git-save.sh --commit-msg "Feature: Update desktop tooling
- Describe the changes
- Tests: Describe validation"
```

The helper stages all tracked changes, deletions, and non-ignored untracked
files, then creates a local commit. Review `git status --short` first, including
generated configuration exports; these are included unless ignored. It never
pushes. Multiline messages and Git hook failures are preserved. The helper
targets the repository containing the script, not the caller's directory;
the neighboring Oscar repository's helper cannot save oscar-ui changes.

## Verification

**Java CI** builds the JAR and runs service tests with Java 21 on Ubuntu 22.04,
Ubuntu 24.04, and macOS 15 for pushes and pull requests, or on manual dispatch.
The CI badge shows the latest `main` workflow result and becomes passing only
when all three platform jobs succeed. The Java CI badge also links to the live
workflow status; platform badges identify
matrix targets. Each job uploads its JUnit reports, including on failure.
Native Qt UI tests are excluded from this Java-only matrix. macOS coverage
currently means Java compilation and service tests; macOS desktop packaging
and native GUI support are not implemented.

`./scripts/unix/desktop.sh build` builds the desktop bundle and runs the service
tests. `./scripts/unix/desktop.sh smoke` stages Qt and checks the native workspace,
menus, tree-to-text selection, and clipboard offscreen with a public sample.

After those commands, run the opt-in native dialog tests on Linux:

```sh
QT_QPA_PLATFORM=offscreen \
LD_LIBRARY_PATH="$PWD/target/app/qt/lib" \
QT_PLUGIN_PATH="$PWD/target/app/qt/plugins" \
./mvnw -B -Dmaven.repo.local="$PWD/.m2" -Doscar.ui.tests=true test
```

Native dialog tests are skipped in ordinary Maven runs without this opt-in.
See the [verification checklist](checklist.md) for coverage, release checks,
and outstanding desktop verification. The checklist is not evidence that a
particular platform or release passed; record actual commands and results.

## Architecture and feature documentation

- [UI architecture](ui-architecture.md): component ownership, document lifecycle,
  editing, saved-byte inspection, masking, and settings.
- [Verification checklist](checklist.md): repeatable checks and test-suite mapping.
- [Development TODO](TODO.md): deferred requirements and known blockers.
- [UI user guide](../ui/README.md): task-based instructions for each feature.
- [Configuration examples](../../cm-config/README.md): sample files and limitations.

## Check Shared Secret

Tools → Check Shared Secret checks a selected binary through OSCAR 3.2.0.
See the [architecture](ui-architecture.md#check-shared-secret) for compatible
profile selection, result semantics, and secret lifetime.

## Source license headers

Project-owned source uses short copyright and Apache-2.0 SPDX headers, with the
full terms in root `LICENSE`. Check headers before committing:

```bash
python3 tools/development/check-license-headers.py
```

Add missing headers with `--fix`. Existing years and notices are preserved;
conflicting or incomplete notices are reported for manual review. The checker
covers Java source, development/build scripts, and owned installer/Git hooks,
and excludes Maven Wrapper, generated files, and dependencies. Local pre-push
validation and Java CI run the read-only check automatically.

For native DEB/MSI builds, see [Build local installers](build-images.md).
