# Development

[Back to OSCAR-UI](../README.md)

This guide covers building from source, testing, and current implementation
behavior. For ready-to-run downloads, use the installation instructions in the
[README](../README.md).

Run development commands from the repository root. Use Java 21 and the
project-local bootstrap tools; no machine-specific checkout location is required.
Keep personal configuration files in the ignored `test-config/` directory.

- [UI component ownership](ui-architecture.md)
- [Release packaging and version tags](packaging.md)
- [Agent instructions](../AGENTS.md)
- [Coding style](../skills/coding-style/SKILL.md)

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
bash scripts/test-install.sh
```

### Run the application

Run commands from the repository root in a terminal on your graphical desktop:

```bash
./scripts/desktop.sh build
./scripts/desktop.sh run
```

For a VM, open the terminal inside the VM's graphical desktop so the application
uses that session's display. Keep the terminal open while using the application;
Ctrl+C stops it. Rebuild after Java source changes because `run` uses the existing
build. Remote GUI sessions require their own display setup.

## Oscar dependency

Until Oscar is published to Maven Central, use the prebuilt thin JAR from
[Oscar v3.1.1](https://github.com/Comcast/Oscar/releases/tag/v3.1.1).
Bootstrap verifies its pinned SHA-256 and installs the JAR with its embedded
Maven POM into this project's `.m2` cache. Dependencies still come from Maven
Central; no Oscar source checkout or source build is required.
Oscar 3.1.1 fixes the classifier-error names to ErroredParameter, ErrorCode,
and ErrorMessage so those sub-TLVs can be edited. Older text templates with
spaced spellings must update those names or be regenerated.

Use Java 21. On Linux/macOS, bootstrap also requires `curl` and either `sha256sum`
or `shasum` (available on macOS):

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

## Saving Repository Changes

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
They also cover double-click editing, deletion/cancellation, CRLF selection,
Binary/Text routing, and masked session-only Secrets settings.
Binary-save tests also cover direct exports, required secrets, cancellation,
overwrite conflicts, source protection, and retention of unsaved text edits
after binary export during a pending replacement prompt.
Binary-open tests cover menu/shortcut routing, explicit type selection,
decoded pane agreement, first Text Save, cancellation, malformed input, and
preservation of the current document. Service fixtures also cover DOCSIS edition
isolation, DPoE decoding, unsupported PacketCable SNMP, repeated entry order,
origin retention during edits, aliases, and input size limits.
Native dialog tests are skipped in ordinary Maven runs without this opt-in.
Container tests cover immediate-only scaffolding, explicit nested population,
registry/version isolation, placeholder deletion, no selection-triggered edits,
last-child removal without automatic refill, and text fidelity.
Service tests cover all discovered template types, nested insertion, text fidelity,
safe-save failures, and external-change detection. Windows desktop behavior has
not yet been verified.

The window title identifies the file; **Text** or **Binary** appears at the
bottom right. Binary imports use their original filename until saved as text.
Operation feedback appears only when needed.

## Current workspace

TLV descriptions in Add and Edit reflow source line breaks into paragraphs,
with a blank line after every two prose sentences. The formatter preserves
technical wording, lists, and aligned examples without rewriting registry data.

The left pane displays an ordered configuration tree with Configuration, TLV,
and Value columns. TLV paths use dotted notation (for example, `22.8.3`).
The right pane displays
original text or decoded binary text. Selecting an entry highlights its text.
Both panes update together; text edits preserve comments, repeated entries,
and unrelated formatting.

- **File > New** discovers text types from Oscar's grammar and bundled registries.
	Choose **Empty** or **All TLVs**, generated through Oscar's template API.
- **File > Open** detects Text or Binary from content with one file picker.
  Text headers select the Oscar catalog. Binary decoding tries compatible
  catalogs and checks that every TLV payload survives the text representation,
  including nested entries. The older DOCSIS profile is preferred for shared
  encodings; 4.0-only supported TLVs select DOCSIS 4.0. This identifies a
  compatible catalog, not the modem's exact version. No format/version prompt
  is required, and filename extensions do not decide the format.
  Binary files open as unmodified binary documents without needing a secret.
  SNMP-bearing inputs, unknown TLVs, malformed data, and lossy imports are
  rejected without replacing the current document. Input and decoded text
  are limited to 1 MiB. Opening does not verify MICs or full configuration
  validity. Successful Open and New offer **Add TLV**.
- **Edit > Add TLV** and the tree context menu offer available children of the
	selected container, with searchable names, paths, and descriptions. Add and Edit show the data
	type with an input example or range instead of catalog metadata.
	The TLV chooser has **TLV** and **Name** columns. Click a header to toggle
	numeric or alphabetical sorting; TLV paths use dots without brackets.

Adding a container also inserts its immediate registry children as blank
placeholders. Nested containers stay empty until **Add TLV** or double-click
explicitly populates that level. Delete unwanted children and enter supported
values through the existing tree actions; selection alone never edits a file.
Child counts follow the selected catalog, not fixed numeric ranges. Unsupported
codecs remain read-only but removable;
deferred DOCSIS 4.0 scope is excluded. Placeholders are not deployment defaults.

- **Double-click a scalar TLV** (or **Edit > Edit TLV**) to open its current
	value in the popup. Apply updates that entry; Cancel leaves it unchanged.
	The Edit popup also offers **Delete TLV** with confirmation. Containers can
	be opened with **Edit TLV**; deleting one removes all its child TLVs.
- **Edit > Duplicate TLV** or the tree context menu copies a selected top-level
	TLV and all its children immediately after the original, then selects the copy.
	Use this to reuse service flows; edit copied identifiers and values as needed.
	Catalog instance limits and protected entries are respected. Save writes the changes.
- **Edit > Del TLV** and the tree context menu delete the selected entry after
	confirmation. Deleting a container includes its children. Protected entries
	remain read-only.
- **File > Save** and its shortcut save the current file in its known format
  without a format or destination prompt. A new document defaults to Text and
  asks for its first destination.
- **File > Save As > Text / Binary** chooses a format and destination. The
  Save As shortcut offers the same formats. Success changes the active file
  and clears the modified state; later Save uses that file and format.
  Both formats use atomic replacement and external-change checks; Save As
  confirms overwrites. Text cannot replace an imported binary origin, and
  binary output cannot replace the current text source or its aliases.
- Binary saving uses Oscar directly, without a Check Config gate, and prompts
  for a session shared secret only when required and missing. Saving an opened
  binary writes back to that file and refreshes decoded text and generated MICs.
  New, Open, and Exit use Save/Discard/Cancel; a successful Save in either
  format completes the pending action. Encoding errors leave the file and
  modified document unchanged. Successful encoding is not full validation.
- **File > Check Config** remains an optional draft-check preview. Minimum
	rules and complete value/length/dependency validation remain unfinished.
- **Settings > Global > Show CM/CMTS MIC TLVs** is unchecked by default.
  Enable it to view the generated, read-only MIC entries in the configuration
  tree. This session preference does not change source text or binary export.
- **Settings > Secrets** provides a masked **shared secret** field.
	Apply keeps its value for this application session only;
	Clear empties the field and takes effect on Apply. Cancel keeps prior values.
	No secrets are encrypted or persisted. Binary export uses the session shared
	secret where required; the community string is not automatically injected.
	The **Hash community strings** checkbox keeps a session-only preference;
	it does not hash or transform values yet. Its algorithm and export behavior
	remain requirements to define.
- **Settings > Global** opens a dialog with **Appearance: Light / Dark**.
  Select **OK** to apply the theme and MIC visibility; **Cancel** keeps the
  previous settings. These preferences apply for this session only.

The binary adapter has lab tests with public test keys and independently
calculated legacy CM/CMTS MICs: CM-SP-MULPIv3.1-I21-201020, D.1.3.1 / D.2.1,
printed pp. 736-738; and CM-SP-MULPIv4.0-I12-261014, D.1.3.1 / D.2.1,
printed pp. 860-862. End/pad markers follow C.1.2.1 / C.1.2.2 in each issue.
The pinned Oscar compiler emits an explicit zero pad byte after the end marker
before word alignment; fixture expectations preserve that implementation detail,
not a requirement to add that byte to every conforming file. These limited
fixtures do not establish complete-file validity or device interoperability.
DPoE and PacketCable adapter tests establish API dispatch, not protocol conformance.
Independent binary-open fixtures use `03 01 01`, the Network Access Control
Object (type 3, length 1, value 1): CM-SP-MULPIv3.1-I21-201020, C.1.1.3,
printed p. 594; separately CM-SP-MULPIv4.0-I12-261014, C.1.1.3, printed p. 695.
These are TLV fragments, not complete deployable configurations. Import compares
decoded/reparsed TLV payloads against the original snapshot to reject loss; it
does not claim byte-identical padding or independently verified decoder coverage.

All TLVs files include a reference-template comment, retained when saving.
This allows reopening Oscar's unmapped legacy placeholders as read-only tree
entries. The adapter replaces Oscar's `PacketCable-X.X` template header with
the selected supported header, without changing its generated body.

This is **draft text editing**, not deployable-file validation. The text pane is
read-only; use the tree actions to add, edit, or delete entries. Validation checks
catalog membership, available instances,
scalar syntax, integer syntax, and unsigned codec width. Registry descriptions
are not interpreted as enums, defaults, or complete validation rules. Length,
dependency, integrity, and device-interoperability checks remain incomplete.
Unsupported codecs, SNMP insertion/import/export, and certificate signing are
unavailable. Binary export delegates supported integrity generation to Oscar;
it does not establish complete integrity coverage. DOCSIS 4.0 L2VPN and eDOCSIS/eSAFE editing remain
deferred. Existing values and generated placeholders are not endorsed as valid
deployment settings. The DOCSIS 4.0 catalog is used as supplied by Oscar, not
merged with its separate legacy DOCSIS catalog.


### Oscar SNMP mapping cache

Oscar owns the SNMP symbol mapping bundled in its dependency JAR. OSCAR-UI
does not maintain a separate mapping. Oscar 3.1.1 may initialize
`res/DottedTextualNetSNMPMap.json` relative to the working directory at runtime;
this generated cache is ignored by Git.

### Inspect binary files

After opening a binary, choose **Inspect → HexDump** for Oscar's hex/ASCII view,
or **Inspect → TLV breakdown** for top-level TLV bytes in file order. Both views
are read-only and show the last opened or saved binary snapshot, not unsaved
edits. Text documents disable these actions. Nested TLVs remain in their parent
hex rows; the Configuration tree provides the decoded hierarchy.
