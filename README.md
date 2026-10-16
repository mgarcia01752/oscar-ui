# oscar-ui

**Work in progress.**

A cross-platform desktop interface for Comcast Oscar, built with Qt Jambi for Linux and Windows.

## Current workspace

The left pane displays an ordered configuration tree; the right pane displays
the original text. Selecting an entry highlights its source text. Both panes
update together, preserving comments, repeated entries, and unrelated formatting.

- **File > New** discovers text types from Oscar's grammar and bundled registries.
	Choose **Empty** or **All TLVs**, generated through Oscar's template API.
- **File > Open** detects the header through Oscar and loads its matching catalog.
	Successful Open and New operations offer an **Add TLV** dialog.
- **Edit > Add TLV** and the tree context menu offer available children of the
	selected container, with searchable names, paths, descriptions, and metadata.
- **Save / Save As** write UTF-8 text using same-directory atomic replacement,
	with overwrite confirmation and checks for external file changes.
	New, Open, and Exit protect unsaved changes with Save/Discard/Cancel.

All TLVs files include a reference-template comment, retained when saving.
This allows reopening Oscar's unmapped legacy placeholders as read-only tree
entries. The adapter replaces Oscar's `PacketCable-X.X` template header with
the selected supported header, without changing its generated body.

This is **draft text editing**, not deployable-file validation. The text pane is
read-only; editing currently adds entries, rather than modifying or deleting
existing entries. Validation checks catalog membership, available instances,
scalar syntax, integer syntax, and unsigned codec width. Registry descriptions
are not interpreted as enums, defaults, or complete validation rules. Length,
dependency, integrity, and device-interoperability checks remain incomplete.
Unsupported codecs, SNMP insertion, binary import/export, signing, and automatic
MIC generation are unavailable. DOCSIS 4.0 L2VPN and eDOCSIS/eSAFE editing remain
deferred. Existing values and generated placeholders are not endorsed as valid
deployment settings. The DOCSIS 4.0 catalog is used as supplied by Oscar, not
merged with its separate legacy DOCSIS catalog.

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

After a successful setup, launch with `./scripts/desktop.sh run`. A graphical
desktop is required for normal use. Native installer packaging tools such as
`rpm` and `fakeroot` are not part of development setup.

## Oscar dependency

Until Oscar is published to Maven Central, use the prebuilt thin JAR from
[Oscar v3.1.0](https://github.com/Comcast/Oscar/releases/tag/v3.1.0).
Bootstrap verifies its pinned SHA-256 and installs the JAR with its embedded
Maven POM into this project's `.m2` cache. Dependencies still come from Maven
Central; no Oscar source checkout or source build is required.

Use Java 21. On Linux, bootstrap also requires `curl` and `sha256sum`:

```sh
scripts/bootstrap-oscar.sh
./mvnw -B -Dmaven.repo.local="$PWD/.m2" test
```

On Windows, run from PowerShell:

```powershell
./scripts/bootstrap-oscar.ps1
./mvnw.cmd -B "-Dmaven.repo.local=$PWD/.m2" test
```

The desktop build command (`scripts/desktop.sh build` or
`scripts/desktop.ps1 build`) uses the same cache. Bootstrap also downloads the
release's original license and notice for inclusion in desktop packages.

## Verification

`./scripts/desktop.sh build` builds the desktop bundle and runs the service
tests. `./scripts/desktop.sh smoke` stages Qt and checks the native workspace,
menus, tree-to-text selection, and clipboard offscreen with a public sample.

After those commands, run the opt-in native dialog tests on Linux:

```sh
QT_QPA_PLATFORM=offscreen \
LD_LIBRARY_PATH="$PWD/target/app/qt/lib" \
QT_PLUGIN_PATH="$PWD/target/app/qt/plugins" \
./mvnw -B -Dmaven.repo.local="$PWD/.m2" -Doscar.ui.tests=true test
```

These tests cover New, All TLVs, Add TLV, first Save, cancellation, unsaved-close
prompts, failed Open, overwrite refusal, and changes during overwrite confirmation.
Native dialog tests are skipped in ordinary Maven runs without this opt-in.
Service tests cover all discovered template types, nested insertion, text fidelity,
safe-save failures, and external-change detection. Windows desktop behavior has
not yet been verified.
