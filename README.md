# oscar-ui

**Work in progress.**

A cross-platform desktop interface for Comcast Oscar, built with Qt Jambi for Linux and Windows.

## Current workspace

The left pane displays an ordered configuration tree; the right pane displays
original text or decoded binary text. Selecting an entry highlights its text.
Both panes update together; text edits preserve comments, repeated entries,
and unrelated formatting.

- **File > New** discovers text types from Oscar's grammar and bundled registries.
	Choose **Empty** or **All TLVs**, generated through Oscar's template API.
- **File > Open > Text** reads UTF-8 text, detects its header through Oscar,
  and loads the matching catalog. **Open > Binary** asks for an explicit type
  and decodes a bounded byte snapshot through Oscar, without an export secret.
  Extensions are filters, not format/version evidence. The Open keyboard
  shortcut offers Text / Binary. Successful Open and New offer **Add TLV**.
  Binary imports become unsaved text drafts; first Text Save chooses a new
  path. Original bytes remain separate, and neither save format can overwrite
  the binary origin or its aliases. Decoded text cannot recover source comments
  or formatting. SNMP-bearing inputs, unknown TLVs, malformed data, and imports
  whose TLV payloads cannot be preserved are rejected without replacing the
  current document. This currently includes Oscar's SNMP-bearing PacketCable
  output. Input and decoded text are limited to 1 MiB; opening verifies neither
  MICs nor complete configuration validity.
- **Edit > Add TLV** and the tree context menu offer available children of the
	selected container, with searchable names, paths, descriptions, and metadata.

Adding a container also inserts its immediate registry children as blank
placeholders. Nested containers stay empty until **Add TLV** or double-click
explicitly populates that level. Delete unwanted children and enter supported
values through the existing tree actions; selection alone never edits a file.
Child counts follow the selected catalog, not fixed numeric ranges. Unsupported
codecs and legacy spaced-name placeholders remain read-only but removable;
deferred DOCSIS 4.0 scope is excluded. Placeholders are not deployment defaults.

- **Double-click a scalar TLV** (or **Edit > Edit TLV**) to open its current
	value in the popup. Apply updates that entry; Cancel leaves it unchanged.
- **Edit > Del TLV** and the tree context menu delete the selected entry after
	confirmation. Deleting a container includes its children. Protected entries
	remain read-only.
- **Save / Save As > Text** write UTF-8 text using same-directory atomic replacement,
	with overwrite confirmation and checks for external file changes.
	New, Open, and Exit protect unsaved changes with Save/Discard/Cancel.
	Save keyboard shortcuts offer a Binary/Text choice.
- **Save / Save As > Binary** exports directly through Oscar, without a
	Check Config gate. It prompts for a session shared secret when required, then
	for a destination, and uses overwrite confirmation and atomic replacement.
	The editable text file cannot be the binary destination. Export leaves the
	source path and unsaved text edits unchanged. Oscar encoding errors still
	block writing; successful export is not complete configuration validation.
- **File > Check Config** remains an optional draft-check preview. Minimum
	rules and complete value/length/dependency validation remain unfinished.
- **Settings > Global > Secrets** provides masked **community string (commstring)** and
	**shared secret** fields. Apply keeps values for this application session only;
	Clear empties the fields and takes effect on Apply. Cancel keeps prior values.
	No secrets are encrypted or persisted. Binary export uses the session shared
	secret where required; the community string is not automatically injected.
	The **Hash community strings** checkbox keeps a session-only preference;
	it does not hash or transform values yet. Its algorithm and export behavior
	remain requirements to define.
- **Settings > Global > Appearance > Dark / Light** switches the application
	and its dialogs immediately. The mode applies for this session only.

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

### Run on this VM's desktop

From a terminal opened inside the VM's graphical desktop:

```bash
cd /home/dev01/Projects/oscar-ui
./scripts/desktop.sh run
```

From an SSH or agent terminal on this VM, run the following as `dev01` while
that user is logged into the VM's graphical desktop. The window appears in
the VM console. This reads the current desktop session's display and
authorization file, whose name can change between logins:

```bash
cd /home/dev01/Projects/oscar-ui
while IFS= read -r setting; do
  case "$setting" in
    DISPLAY=*|XAUTHORITY=*) export "$setting" ;;
  esac
done < <(systemctl --user show-environment)
QT_QPA_PLATFORM=xcb ./scripts/desktop.sh run
```

Keep the terminal open while using the application; Ctrl+C stops it. After
changing Java source, run `./scripts/desktop.sh build` before launching again
because `run` uses the existing build. The application is still in development;
see the current editing limitations above.

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
