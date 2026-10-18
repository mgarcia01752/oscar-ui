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
| `GlobalSettingsDialog` | Session appearance and MIC visibility preferences |
| `SecretsDialog` | Masked session secret and hashing preference |
| `SaveFormatDialog` | Text/Binary choice; the main window performs the save |
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
[README](../README.md) after moving UI responsibilities.
