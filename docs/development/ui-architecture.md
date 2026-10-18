# UI component ownership

`MainWindow` arranges panes, owns actions and document state, and coordinates
asynchronous service operations. Widgets and their callbacks run on the Qt UI
thread. Oscar parsing, validation, and serialization remain in the service layer.

| Component | Responsibility |
| --- | --- |
| `ConfigurationTreePane` | Tree rows, TLV-to-row mapping, parent lookup, selection, MIC visibility, and context menus |
| `ConfigurationTextPane` | Read-only source preview and selection highlighting, including CRLF position conversion |
| `TlvEditorDialog` | Shared Add/Edit controls, value help, validation, and delete confirmation |
| `NewDocumentDialog` | New configuration and reference-template choices |
| `GlobalSettingsDialog` | Persisted appearance choice and session MIC visibility |
| `SecretsDialog` / `CustomSnmpRulesPane` | Session shared secret, SNMP masking, and custom object rules |
| `SaveFormatDialog` | Text/Binary choice; the main window performs the save |
| `BinaryInspectionDialog` | Read-only, monospace HexDump and top-level TLV listings |
| `BinaryCheckDialog` | Configuration-check results |
| `TlvDescriptionFormatter` | Presentation-only description reflow |

Panes keep their internal maps private and expose domain operations. The tree
emits normal Qt selection signals; the main window connects them to text
highlighting and action availability. Context menus reuse the main window's
actions so permissions and shortcuts have one owner.

Qt parent ownership manages child widgets. Temporary dialogs and context menus
are disposed after use. Stable widget object names remain available to UI tests.
Small labels, standard file dialogs, and message boxes stay with their owner;
they do not require separate classes.

Parsed TLV instances use descriptive `Node` components (`tlvPath`, `tlvName`,
`encodedValueText`, `childTlvs`, and `isContainer`). Source ranges use UTF-16
offsets with an exclusive end; `terminatorOffset` identifies a closing brace
or semicolon. These editing instances remain separate from Oscar's registry
definitions. Catalog lookup uses an extensible `RegistryFamily` key, while
the UI's editable-codec allowlist deliberately supports fewer codecs than Oscar.

Follow [coding-style](../../skills/coding-style/SKILL.md) when adding or changing
components. Run the service tests and the native Qt tests described in the
[development guide](README.md) after moving UI responsibilities.

## TLV editor terminology

Add and Edit dialogs label a TLV that contains child TLVs as **Parent TLV**,
using the help text **Parent TLV | Add values to its child TLVs**. Use this
user-facing term instead of **Container**. Values are entered on the child
TLVs; the parent does not expose an editable scalar value. Internal container
identifiers and Oscar APIs retain their existing names.

## Binary inspection

Inspect follows Settings in the menu bar. The menu itself and its HexDump and
TLV breakdown actions are enabled
only when idle with an active binary document (`path == null`, `origin != null`).
Opening text or saving as text disables both even if the document retains a
binary origin for overwrite protection. New text drafts also disable inspection. Hovering over the disabled Inspect
menu explains that a binary file must be selected. The menu-bar action is
explicitly disabled, and mouse tracking supplies the hint even for disabled
actions; while busy, it asks the user
to wait for the current operation.

`OscarBinaryService` renders the immutable bytes from the last open/binary save
on the existing worker. It does not reread disk, encode edits, sign data, or log
contents. The dialog labels this snapshot and excludes unsaved edits.
HexDump uses Oscar `HexDump.dumpHexString`. TLV breakdown matches Oscar's
`--hex toplevel` type-labelled rows, but reuses the existing length-aware payload
walker (`TlvDictionary.getTopLevelByteLength`, `TlvBuilder.nextTLVIndex`) and
`HexString` formatting: Oscar 3.1.2 `TlvBuilder.tlvDump` assumes one-byte lengths.
Rows preserve top-level instance order and nested bytes; padding/end markers
appear only in HexDump. This is not a recursive decoded TLV tree.

Inspection checklist:

- [x] Menu order and binary-only enablement, including busy states.
- [x] Disable the Inspect menu for no file/text; show a prerequisite hover hint.
- [x] Read-only, selectable, monospace listings with no line wrapping.
- [x] Original snapshot retained after edits or external file deletion.
- [x] Repeated TLV order, padding/end-marker handling, and text-save gating.
- [ ] Verify native dialogs on Windows.

## Keyboard shortcuts

Use Qt standard key bindings, with one action owning each shortcut. File → Save
owns Ctrl+S directly and displays it in the menu. It saves the active format:
text to its text path, binary to its binary path, and a new text document through
the destination chooser. Saving is disabled while busy or without changes.

| Action | Windows / Linux shortcut | Behavior |
| --- | --- | --- |
| New | Ctrl+N | Create a document, with an unsaved-change prompt if needed |
| Open | Ctrl+O | Open a text or binary configuration |
| Save | Ctrl+S | Save in the current format |
| Save As | Ctrl+Shift+S | Choose Text or Binary, then a destination |
| Copy | Ctrl+C | Copy selected configuration text |
| Select All | Ctrl+A | Select all configuration text |
| Quit | Qt platform default | Close with an unsaved-change prompt; window close shortcuts also apply |

Qt resolves platform-specific standard bindings; macOS generally uses Command
instead of Control, but native macOS UI support is not verified. Copy and Select
All also follow the focused text widget's behavior in dialogs.

Hotkey checklist:

- [x] One Save action for menu clicks and Ctrl+S; no duplicate shortcut binding.
- [x] Ctrl+S keyboard event saves a new text document through its chooser.
- [x] Save uses the active binary format without a format-selection prompt.
- [x] Save enablement follows pending operations and modified state.
- [ ] Verify physical keyboard shortcuts on Windows.

## System appearance and JSON preferences

`Appearance` has SYSTEM (default), LIGHT, and DARK choices. `DesktopTheme`
resolves SYSTEM using Qt 6.8
[`QStyleHints.colorScheme`](https://doc.qt.io/qt-6.8/qstylehints.html#colorScheme-prop); an unknown system scheme
falls back to Light. A queued `colorSchemeChanged` connection reapplies the chosen
palette after Qt processes the system change. Explicit Light/Dark choices remain
fixed. The connection is disconnected when the window closes. Icons follow the
resolved palette. OS notification delivery depends on the desktop/Qt platform.

`AppearanceSettingsStore` owns only the per-user JSON schema and file I/O.
`MainWindow` locates `settings.json` through `QStandardPaths.AppConfigLocation`,
loads it before displaying the window, and saves on Settings → Global → OK.
Cancel never writes. Successful persistence precedes applying the choice; write
failure reports an error and keeps the previous settings. Missing files default
to System; malformed/unreadable files produce a visible status message and are
not overwritten at startup. Saves use a sibling temporary file and atomic rename
where available, with replacement fallback on filesystems without atomic moves.

```json
{
  "version": 1,
  "appearance": "system",
  "obfuscateSnmpSecrets": false
}
```

Appearance and the masking checkbox are persisted: no shared secret, community values, configuration
contents, or MIC visibility. Settings are outside the installation directory and
survive uninstall. Noninteractive smoke/tests skip user settings unless a test
injects a temporary settings store.

Appearance checklist:

- [x] Default to System; retain explicit Light and Dark choices.
- [x] Follow system color-scheme notifications without overriding explicit choices.
- [x] Persist the selection in per-user JSON and restore it on restart.
- [x] Cancel leaves preferences unchanged; failed writes preserve prior state.
- [x] Reject malformed/unsupported JSON without changing its contents.
- [x] Keep all secrets and document contents out of the settings file.
- [ ] Verify real desktop theme notifications on Ubuntu and Windows.

## About and release checks

`AboutDialog` owns the branded About panel, project links, build versions, and
manual update status. A Program | Version | Link grid lists OSCAR-UI, OSCAR,
and Qt Jambi with clickable GitHub links. The OSCAR-UI license links to the
repository’s `LICENSE` file on GitHub. Maven filters `build-info.properties` from the project,
Oscar, and Qt Jambi versions so packaged and development builds report their
actual build versions. About → Check for updates opens the same dialog and
starts the check; the dialog also offers a retry button.

`ReleaseUpdateService` reads GitHub's latest published stable OSCAR-UI release
API without credentials. It compares numeric major/minor/patch components with
the installed version, treating an equal-number development build as older than
the stable release. Drafts and prereleases are excluded. Unknown versions and
invalid metadata are reported as check failures, never as up-to-date results.
The dialog uses a worker and UI-thread timer, disables duplicate requests, and
cancels its work on close. Connections have finite timeouts and bounded response
size. No document, secrets, or settings are sent. Checks are user initiated;
updates are downloaded manually through the GitHub releases link.

About checklist:

- [x] Show OSCAR-UI branding and build-derived dependency versions.
- [x] Link both OSCAR and OSCAR-UI GitHub repositories.
- [x] Compare numeric release versions, including development builds.
- [x] Keep the UI responsive while checking; report offline failures.
- [x] Exercise successful and failed checks using deterministic native tests.
- [ ] Verify external browser launching on installed Ubuntu and Windows builds.

Window captions use OSCAR-UI only for the main window. Dialogs use task titles
such as Add TLV, Settings - Global, or About without an application suffix.
Startup leaves Qt's application display name unset, preventing its native
platform title formatter from appending OSCAR-UI to dialog captions. The
application name and desktop-file identity remain OSCAR-UI identifiers.

## OSCAR TLV comments in the text pane

Settings → Global includes **Show TLV comments in configuration text**, checked
by default each session. Binary import calls OSCAR's
`ConfigurationFileExport.toPrettyPrint(EXPORT_FOUND_TLV, false)`; the second
argument disables comment suppression. OSCAR supplies inline comments such as
`/* TLV: [25.1]*/`. The UI does not generate its own annotations or number column.

The checkbox controls preview visibility only. Hiding comments leaves the
underlying source, edits, and saved files intact. Source-offset mapping preserves
selection highlighting. Text files retain their original contents: existing
OSCAR comments can be hidden, but missing comments are not synthesized. Copy
copies the visible preview. The preference is session-only, like MIC visibility.

- [x] Request OSCAR comments for binary imports by default.
- [x] Apply visibility on OK; Cancel preserves the previous choice.
- [x] Preserve source text and selection offsets when hiding comments.

## Selected TLV hex

The configuration tree's right-click menu offers **View TLV Hex…** for a mapped
TLV when idle. It is disabled for the document root, no selection, and unmapped
entries. It works for text and binary documents, independently of the binary-only
Inspect menu. Double-click continues to edit.

`SelectedTlvHexService` encodes the current selected instance through OSCAR's
`ConfigurationFileImport`, wrapping nested selections in their ancestor names.
It uses registry `lengthBytes`, `TlvBuilder.nextTLVIndex/getTlvValue`, and
`HexString` to build the rows. No file compiler, MIC regeneration, shared secret,
or disk writes are involved. Current unsaved edits are included. Repeated TLVs
are selected by node identity, not by their shared numeric path. Unexpected
encoding boundaries produce an error rather than a misleading breakdown.

`TlvHexDialog` displays a read-only **Name | Hex** tree, expanded initially.
The selected TLV is the first row; child and nested parent rows appear below it
with hierarchical indentation. Every row includes its complete type, length,
and value bytes; a parent's hex therefore includes its children. A scalar has
one row. Encoding runs on the existing worker, keeping the UI responsive.

- [x] Show parent and nested child bytes with indentation.
- [x] Preserve repeated-instance selection and current edits.
- [x] Support scalar selection and OSCAR's DOCSIS 4.0 length widths.
- [x] Disable the action while busy and for non-TLV selections.

Double-clicking the **Name** column in the TLV hex popup opens a separate,
read-only description dialog for that exact row, including nested children.
Descriptions come from OSCAR's registry and use the shared paragraph formatter.
Missing descriptions show an explicit fallback. Double-clicking Hex does not
open a description; expansion remains available through the tree arrows.

- [x] Open the selected hex row's OSCAR description on name double-click.

## Compile text before opening

Opening UTF-8 configuration text first runs OSCAR's
`ConfigurationFileImport.toByteArray()` on the existing worker. This checks
syntax and unsigned TLV encoding without a shared secret, MIC generation, file
writes, or source rewriting. `BinaryTreeProjection` retains the encoded result
and walks its physical instances using OSCAR's registry length widths and TLV
boundary APIs. OSCAR exports each top-level instance separately to decode its
values; this prevents the whole-file exporter from grouping repeated types.
Child rows follow the actual binary byte order. OSCAR may order children by
its dictionary rather than their source positions; the original text remains
unchanged and each row still targets its corresponding source occurrence.
If OSCAR collapses repeated child instances, import fails rather than silently
omitting them.

The source mapping uses OSCAR's full `begin()` grammar, just as its importer
does. Calling `startTLV()` outside that enclosing rule allowed SNMP values to
consume later statements and comments. Binary instances are matched to source
occurrences with count/type checks; a mismatch fails instead of showing
misleading rows. Displayed scalar values come from the binary decode, while
source offsets retain exact edit, delete, duplicate, and highlight targets.
SNMP source spans end at their semicolon, excluding trailing remarks. The right
pane preserves original comments, whitespace, spelling, and TLV order exactly.
Completed edits rebuild the binary projection on the existing worker. Empty or
incomplete drafts and reference templates retain their editing scaffold with
an explicit **Draft — not compiled** label; they are not a binary preview.
This is an encoder check, not modem/CMTS admission validation or a signed
deployment binary.

`OscarTextCompileCheck` captures OSCAR exceptions and printed encoder failures,
including failures swallowed upstream. Diagnostics are bounded and shown to the
user without printing input to the console; stack frames are omitted. Scoped
console routing captures only the compiling thread and restores the original
streams in a finally block. Other threads retain their console destinations.
A failed text check does not fall back to binary decoding or replace the current
document. Empty files and incomplete reference templates are rejected when
OSCAR cannot encode them; creating/editing new drafts remains available.

- [x] Compile text through OSCAR before display, without signing credentials.
- [x] Preserve original source and repeated/nested TLV ordering.
- [x] Display OSCAR diagnostics and retain the prior document on failure.
- [x] Validate the example containing SNMP read/write communities.
- [x] Build loaded TLV rows from OSCAR-encoded instances and decoded values.
- [x] Keep all twelve example SNMP statements separate from remarks.
- [x] Verify binary child order and source targeting after edits.
- [x] Label incomplete editing scaffolds as uncompiled drafts.


## SNMP display obfuscation

**Settings → Secrets → Obfuscate SNMP secrets** replaces the previous inert
hashing checkbox. It changes presentation only: source, document nodes, binary
bytes, compilation, and saved files keep their real values. The shared secret
retains its existing independent password field. No encryption or hashing is
performed, and neither rules nor secrets are persisted in settings JSON.

`SnmpSecretMask` owns immutable session rules and fixed replacements:
`********` in text and `FF FF FF FF` in hex. Built-in arrays match OSCAR TLV
names, a scoped embedded `SecurityName` path, and standard object OID prefixes.
Coverage includes direct community/security-name TLVs, `docsDevNmAccessCommunity`,
SNMP-COMMUNITY-MIB, USM authentication/privacy key changes (including DH), and
USM/VACM security identities. Whole varbinds in identity-indexed tables are
masked conservatively, including non-secret columns whose OIDs expose identities.
These are UI sensitivity rules, not replacement codec or registry definitions.
Definitions: [USM](https://www.rfc-editor.org/rfc/rfc3414.html),
[DH USM](https://www.rfc-editor.org/rfc/rfc2786.html),
[VACM](https://www.rfc-editor.org/rfc/rfc3415.html), and
[community coexistence](https://www.rfc-editor.org/rfc/rfc3584.html).

Custom entries accept an SNMP name or numeric OID, resolve known standard names
or use OSCAR's `NetSNMP.toDottedOID`, and retain a canonical numeric OID.
Malformed/unresolved input is rejected with an inline message. Names cannot
contain command arguments. Leading dots and numeric component spelling are
normalized for duplicate checks; matching uses component boundaries, so `.2`
also covers `.2.1` but not `.20`. Unknown vendor password objects require a
custom rule; arbitrary passwords cannot be inferred from generic Snmp11/Snmp64.
Add/Remove operate on a dialog-local copy, committed only by Apply for Session.

The source preview replaces protected statements, including OID indexes and
internal comments, while maintaining original-to-display offsets for selection.
Standard object labels remain visible; custom statements use `CustomSnmpSecret`
in the masked preview. Comments repeating known protected source literals are
also masked. This does not classify arbitrary secrets written only in comments.
Copy reads the displayed text. Applying the setting immediately refreshes text
and tree without marking the file modified. Existing sensitive editor values
are never placed in the visible input; the fixed placeholder means “keep value”
when left blank. New replacements use password entry. Diagnostic details are
hidden while obfuscation is active because upstream errors may echo input.

Selected-TLV hex masks the entire protected record and every ancestor that
contains its bytes, while preserving unaffected sibling rows. Binary Inspect
uses the original binary snapshot, not the current edited tree, to classify
records. Both inspection modes show a labeled top-level masked preview without
original offsets or ASCII columns. If safe snapshot mapping fails, the entire
listing becomes the fixed hex mask; raw bytes are never used as a fallback.
Masked previews are not compilable/exportable replacement configurations.

- [x] Mask direct SNMP communities and security identities by registry name/path.
- [x] Mask USM/DH credentials and identity-bearing SNMP/VACM indexes.
- [x] Add/remove custom names and OIDs with validation and duplicate checks.
- [x] Use fixed text/hex masks independent of original secret length.
- [x] Refresh text, TLV values, clipboard, and parent hex consistently.
- [x] Preserve selection offsets and keep real values through edit/save.
- [x] Mask the binary snapshot even after unsaved deletion of a secret.
- [x] Keep rules session-only and preserve prior settings on Cancel.

Deferred symbolic SNMPv3/VACM configuration and its acceptance checks are
tracked in [Development TODO](TODO.md#vacm-and-snmpv3-symbolic-configuration).

## Saved masking preference and status

Settings > Secrets > Apply persists `obfuscateSnmpSecrets` alongside appearance
in the per-user settings JSON. Older files without this field default to false.
Shared-secret values and custom SNMP rules remain session-only. Cancel does
not update the preference. Save failures leave masking active for the session
and display an error explaining that persistence failed.

The bottom-right status bar shows **Masked** followed by a native vertical
separator immediately left of Text/Binary when masking is enabled. Both the
label and separator are hidden when disabled. The tooltip explains that saved
configuration values remain unchanged.

- [x] Restore the checkbox on startup and preserve it across appearance changes.
- [x] Keep secrets and custom rules out of the settings JSON.
- [x] Refresh the status indicator on Apply and preserve the setting on Cancel.
- [x] Test restart, legacy settings, disable, and appearance/masking coexistence.

Binary export compiles original source values through OSCAR, including DOCSIS
SNMP statements, regardless of masking. Compile validation captures upstream
failures before writing, and the saved bytes are decoded and compared against
re-encoded TLV payloads before destination replacement. Decode each physical
top-level TLV separately to preserve repeated SNMP entries and source order;
retain OSCAR-generated TLV comments. Unknown or lossy imports remain rejected.
Editable PacketCable import with generated SNMP remains deferred in
[Development TODO](TODO.md). Raw upstream diagnostics remain hidden while masked.

- [x] Save/reopen the community, USM, and VACM fixture without storing masks.
- [x] Preserve compiled bytes after masked preview and repeated binary saves.
- [x] Preserve the existing destination when SNMP compilation fails.

## SNMP statement editing

Snmp11/Snmp64 entries using OSCAR's `oid` codec accept one compound value:
OID, SNMP type, and quoted value. OSCAR parses and compiles the proposed
statement before acceptance. Replacing it updates the complete value span,
not just the first token, and preserves neighboring TLVs and binary origin.
Sensitive existing entries remain hidden in the editor while masking is on;
leaving the field blank keeps the original statement. Enter the full compound
value to replace it. Validation messages do not echo supplied credentials.

- [x] Edit Snmp11 after text-to-binary saving and save/reopen the changed binary.
- [x] Keep the editor and preview masked while retaining real output values.
- [x] Reject additional statements in a proposed SNMP value.
