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
| `SecretsDialog` | Masked session secret and hashing preference |
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

Follow [coding-style](../skills/coding-style/SKILL.md) when adding or changing
components. Run the service tests and the native Qt tests described in the
[development guide](development.md) after moving UI responsibilities.

## Binary inspection

Inspect follows Settings in the menu bar. HexDump and TLV breakdown are enabled
only when idle with an active binary document (`path == null`, `origin != null`).
Opening text or saving as text disables both even if the document retains a
binary origin for overwrite protection. New text drafts also disable inspection.

`OscarBinaryService` renders the immutable bytes from the last open/binary save
on the existing worker. It does not reread disk, encode edits, sign data, or log
contents. The dialog labels this snapshot and excludes unsaved edits.
HexDump uses Oscar `HexDump.dumpHexString`. TLV breakdown matches Oscar's
`--hex toplevel` type-labelled rows, but reuses the existing length-aware payload
walker (`TlvDictionary.getTopLevelByteLength`, `TlvBuilder.nextTLVIndex`) and
`HexString` formatting: Oscar 3.1.1 `TlvBuilder.tlvDump` assumes one-byte lengths.
Rows preserve top-level instance order and nested bytes; padding/end markers
appear only in HexDump. This is not a recursive decoded TLV tree.

Inspection checklist:

- [x] Menu order and binary-only enablement, including busy states.
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
  "appearance": "system"
}
```

Only appearance is persisted: no shared secret, community values, configuration
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
manual update status. Maven filters `build-info.properties` from the project,
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
