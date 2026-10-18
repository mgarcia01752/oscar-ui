# Development and release verification checklist

[Development guide](README.md) · [Architecture](ui-architecture.md) · [Deferred work](TODO.md)

This is a reusable checklist, not a permanent record of passed releases.
Record the tested commit/tag, platform, commands, results, and skipped checks
when using it. Existing tests establish limited behavior, not DOCSIS compliance
or device interoperability. Detailed requirements live in the architecture;
unfinished functionality lives in TODO rather than duplicated feature lists.

## Automated coverage map

| Area | Existing suite / check | Important regression cases |
| --- | --- | --- |
| Registry and document editing | `OscarInspectionServiceTest`, `DocumentEditingTest` | Type/header isolation, templates, nested/repeated instances, immediate-child scaffolding, protected entries, source fidelity, duplicate/delete |
| Text compilation and binary tree | `OscarTextCompileCheckTest`, `BinaryTreeProjectionTest` | Comments around SNMP, physical binary order, source targeting, failed compilation, incomplete drafts |
| Binary open/save | `BinaryOpeningTest`, `OscarBinaryServiceTest` | Content detection, compatible profiles, malformed/lossy input, hashes/aliases, repeated payloads, required secrets, masked save/reopen |
| Inspection and preview | `SelectedTlvHexServiceTest`, `ConfigurationTextPreviewTest` | Exact subtree, repeated instances, nested length widths, TLV comments and selection offsets |
| Masking and preferences | `SnmpSecretMaskTest`, `AppearanceSettingsStoreTest` | Fixed-length masks, identity-bearing OIDs, custom rules, JSON restart/cancel/failure behavior |
| Shared-secret checker | `SharedSecretCheckServiceTest` | Correct/wrong key, standard/extended MICs, malformed or unsupported inputs |
| About and descriptions | `ReleaseUpdateServiceTest`, `TlvDescriptionFormatterTest` | Version comparison, malformed metadata, prose/list/example formatting |
| Native dialogs | `MainWindowTest` (opt-in) | Save shortcuts, popups, selection, overwrite races, masking/clipboard/hex, themes, About, held-eye masking, binary-only Inspect |
| Native launch | `scripts/unix/desktop.sh smoke` | Qt startup, menus, tree/text agreement, clipboard |
| Tooling | `tools/git`, `tools/development`, `scripts/tests` | Safe tags/pushes, verified dependency update/rollback, source bundling, DEB maintainer scripts |

Java and native tests are under [src/test/java](../../src/test/java/io/oscarui).
Use the commands in the [development guide](README.md#verification); use the
[script index](../../scripts/README.md) for platform entry points.

## Before merging implementation changes

- [ ] Run the relevant suites above and record failures/skips explicitly.
- [ ] For Java changes, build with the pinned dependency; for native changes,
  also run opt-in Qt tests and the smoke check. Java-only CI skips native tests.
- [ ] Check New/Open/Save/Save As cancellation and busy-state behavior for
  affected document paths; retain source/destination on failure.
- [ ] Preserve repeated-instance identity, nesting, comments, BOM/CRLF, and
  source-to-binary mapping when changing editing or parsing integration.
- [ ] Verify sensitive values remain masked across affected views and diagnostics,
  while compilation and export still consume original values.
- [ ] Update architecture, user instructions, and example catalog for changed
  behavior; update TODO only when a deferred item is actually resolved.
- [ ] Run `git diff --check` and check Markdown links/anchors. Review untracked
  files and keep real secrets, generated caches, and build output out of commits.

## Before publishing a release

- [ ] Enable the [local pre-push gate](pre-push-checks.md) for this checkout and
  resolve any local failure before uploading a branch or tag.

- [ ] Verify the selected OSCAR version/commit/checksum and both bootstrap pins;
  use the [dependency updater](oscar-dependency-updates.md) for upgrades.
- [ ] Review [configuration examples](../../cm-config/README.md) against tracked
  examples; check new/changed examples compile and describe their limitations.
- [ ] Confirm installer summaries, dependency source versions/checksums, licenses,
  release description, and installation links match actual package contents.
- [ ] Run the Git, dependency, and packaging helper tests plus workflow lint for
  relevant tooling changes; do not push a real tag merely to test a script.
- [ ] Confirm CI passes for the release tag, including all Java matrix jobs.
- [ ] Confirm Windows MSI and Ubuntu DEB build/install/launch/uninstall jobs pass;
  check launcher cleanup and preservation of user configuration files.
- [ ] Confirm both expected assets upload before automatic publication. Failed
  or skipped prerequisites must leave any new release draft unpublished.
- [ ] Record manual desktop checks not covered by CI: dialogs and browser links,
  physical shortcuts, resize/expand/collapse, second-file opening, close/cancel,
  disabled Inspect hint, clipboard masking, and real system-theme notifications.

Release commands, CI dependencies, source bundling, and platform limitations
have one owner: [Release packaging](packaging.md). Outstanding clean-machine
and broader interoperability work is tracked in [TODO](TODO.md#verification-backlog).

### Java redistribution materials

- [ ] Verify the Java source digest against the pinned Adoptium checksum.
- [ ] Pass `verify-java-distribution.py` on extracted DEB and installed MSI.
- [ ] Retain GPLv2/Classpath text and all build-JDK module notice content.
- [ ] Recheck source, version, and checksum pins together on Java upgrades.
