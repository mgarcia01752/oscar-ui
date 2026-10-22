# Multi-file workspace plan

Status: proposed; implementation has not started.

## User-visible behavior

- New and Open create/select a tab instead of replacing the current document.
  Opening another file does not prompt to discard the current file.
- Put tabs immediately above the text panel. Keep one shared TLV tree on the left.
- Tab titles use the filename. New documents use unique Untitled names.
  Italic means unsaved: a new draft or modifications since the last successful
  save. Use normal text after successful save; do not italicize merely because
  a tab is inactive. Add an accessible unsaved indication in the tooltip.
- Match the supplied editor example with two rows above the text preview:
  filename tabs with close buttons, then a breadcrumb row for the active file.
  Show directory segments followed by the filename, for example
  `configs > docsis > customer.cfg`. There is no project root in this app, so
  use the active file's actual parent path, shortening leading segments with an
  ellipsis when space is limited. Always retain the filename; expose the full
  absolute path in a tooltip. Untitled documents show `Unsaved > Untitled 1`.
  Disambiguate identical tab filenames with the shortest distinguishing parent
  path. Breadcrumbs are informational in this first phase, not folder navigation.
  Do not copy the example's Markdown headings or editor-specific toolbar controls;
  retain OSCAR-UI's Update/layout controls and lower-right Text/Binary indicator.
- Switching tabs synchronizes the TLV tree, text, actions, inspection context,
  and bottom-right Text/Binary indicator in one UI-thread activation step.
  Preserve each tab's tree expansion, selected TLV occurrence, and text/tree
  scroll position. A numeric TLV path alone cannot identify repeated instances.
- Text/Binary describes the active document's backing/save representation, not
  the fact that all documents have a text preview. New drafts show Text.
  Binary-opened tabs show Binary. Save As changes it only after successful save.
- Opening an already-open canonical path activates that tab and preserves its
  unsaved edits. Do not infer format from filename extensions alone.
- Closing a dirty tab offers Save / Discard / Cancel. Closing a clean tab is
  immediate. Select an adjacent tab afterward; the last close leaves an empty
  workspace, clears the indicator, and disables document actions.
- Keep tabs accessible in tree-only mode by keeping the tab strip visible even
  when the text preview is hidden. Support overflow scrolling, close buttons,
  Ctrl+Tab / Ctrl+Shift+Tab, and Ctrl+W. Native shortcut conventions apply on Mac.

## Ownership and model

Introduce a focused DocumentSession model and WorkspaceSessionController rather
than adding more unrelated state to MainWindow. A session owns a stable ID,
immutable OSCAR document snapshot, revision, saved revision/dirty state, canonical
file identity, explicit Text/Binary enum, binary origin/save metadata, view state,
and any document-scoped error. MainWindow coordinates widgets and global actions.
Keep OSCAR parsing and encoding in the existing service APIs; no upstream change
is currently required.

Use a tab strip plus the existing shared tree/text widgets initially. Save view
state before activation and restore it after displaying the selected session.
This avoids one native editor/tree and duplicate sensitive previews per tab.
Stable session IDs identify tabs and operations; tab positions are not identities.
File identity resolves relative paths, symlinks, and platform case rules, without
lowercasing all paths on case-sensitive filesystems. Save As must detect a target
already represented by another tab rather than silently overwrite its state.

Global appearance, masking rules, Common TLVs, and banner preferences remain
application settings. Preserve the current shared-secret scope in this first
phase; document it explicitly and capture needed secret material at operation
start rather than reading a mutable global value when a worker finishes.
Do not persist document contents, keys, or reopened sessions automatically.

## Asynchronous operations and saving

Retain serialized OSCAR worker execution initially. Every operation captures the
originating session ID, document revision, format, destination, and needed options.
Results update that session only, even if another tab is now active. A successful
save marks only its captured revision saved; newer edits remain dirty. A failed
save preserves dirty state and the original destination/format. Closed-session
results are discarded. Disable conflicting actions on a busy session while
allowing tab selection; never retarget a queued operation to the active tab.

New/Open inserts a session only after successful generation/parsing; failures
leave every existing tab intact and use the current diagnostic report UI.
Save/Save As/inspection/TLV actions always operate on the active session snapshot.
Existing binary-save validation, MIC handling, disk-change checks, and synthetic
fixture protections remain required. Editing a binary-backed document retains
Binary status until a successful Save As Text.

Exit, installer updates, and uninstall currently handle one dirty document.
Replace that assumption with a shared all-session close review. Collect choices
before removing tabs; Cancel or a failed save aborts the close/update operation
and keeps tabs open. Files already successfully saved remain saved. Do not stop
workers or launch an installer until outstanding operations and saves settle.

## Implementation sequence

1. Extract per-document session state and controller behind the existing single
   workspace; retain existing behavior and tests before introducing tabs.
2. Add tabs/path display and atomically bind active session to tree/text/status.
3. Route New/Open, editing, saves, inspections, and asynchronous completion through
   stable session IDs; preserve dirty and view state and prevent duplicate paths.
4. Add per-tab close and all-session exit/update/uninstall review.
5. Update user workspace/file/About-update guides and capture dark/light examples
   with multiple files, duplicate basenames, dirty tabs, and Text/Binary selection.

## Acceptance checks

- Two text files, two binary files, and mixed file types keep independent values,
  TLV selections, dirty states, save destinations, and indicators.
- New/Open retains existing unsaved work; parse failure creates no broken tab.
- Editing B cannot change A; switching back restores repeated-TLV occurrence
  selection and scroll/expansion without taking nodes from another document.
- Untitled/modified tabs are italic; successful save clears italics; cancellation,
  external-file conflict, or save failure retains unsaved state.
- Duplicate path opens focus the existing tab, including aliases; identical
  basenames in different directories remain distinguishable. Save As collisions
  are handled explicitly.
- Switch A to B during a delayed save/decode: completion updates A only. Stale
  revisions and closed-session completion cannot overwrite current state.
- Save As Text/Binary updates the indicator only on success; tab selection always
  refreshes it. Empty workspace has no stale type label or enabled file actions.
- Dirty close/exit/update/uninstall exercise Save, Discard, Cancel, and failed save
  across several tabs; no operation silently loses another tab's edits.
- Tab overflow, keyboard navigation, tree-only/text-only/both layouts, dark/light
  themes, and Windows/macOS/Linux render coherently. Verify on native CI where
  available; Linux offscreen success alone does not prove other desktop behavior.
- Preserve existing OSCAR round trips, masking, CVC, template, and installer tests.

## Scope boundaries

No directory browser, recursive folder import, drag-and-drop, session restore,
Save All command, or direct editable text mode in the initial implementation.
The current text pane remains a synchronized preview. These can be separate work
items if requested. The supplied editor screenshot confirms the two-row
filename-tab and directory-breadcrumb layout.
