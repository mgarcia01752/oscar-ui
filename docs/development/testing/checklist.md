# Development and release verification checklist

- [ ] On Windows, verify the MSI welcome/completion panel and top banner show
  OSCAR-UI artwork with readable text, including at increased display scaling.

[Development guide](../README.md) · [Architecture](../architecture/ui-architecture.md) · [Deferred work](../todo/TODO.md)

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

Java and native tests are under [src/test/java](../../../src/test/java/io/oscarui).
Use the commands in the [development guide](../README.md#verification); use the
[script index](../../../scripts/README.md) for platform entry points.

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
  use the [dependency updater](../build/oscar-dependency-updates.md) for upgrades.
- [ ] Review [configuration examples](../../../cm-config/README.md) against tracked
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
have one owner: [Release packaging](../build/packaging.md). Outstanding clean-machine
and broader interoperability work is tracked in [TODO](../todo/TODO.md#verification-backlog).

### Java redistribution materials

- [ ] Verify the Java source digest against the pinned Adoptium checksum.
- [ ] Pass `verify-java-distribution.py` on extracted DEB and installed MSI.
- [ ] Retain GPLv2/Classpath text and all build-JDK module notice content.
- [ ] Recheck source, version, and checksum pins together on Java upgrades.

### HexDump acknowledgment

- [ ] Opening HexDump always asks before showing original bytes; Cancel is default.
- [ ] Cancel shows no dump; Yes shows OSCAR's traditional hex/offset/ASCII listing.
- [ ] With masking enabled, TLV breakdown stays masked after viewing HexDump.
- [ ] Reopening HexDump asks again; the saved masking preference is unchanged.

## Structured SNMP editing

- [ ] Add Snmp11 with separate OID, Type, and Value; verify the dropdown matches
  OSCAR's BER API and hints change with the selected type.
- [ ] Reject malformed OIDs, IPv4 addresses, hex bytes, quoted input, and
  out-of-range integers; confirm valid statements compile before Apply.
- [ ] Edit an existing statement with all fields prepopulated; save/reopen binary
  and preserve neighboring TLVs and order.
- [ ] With masking enabled, retain a secret by leaving both fields blank and
  Type unchanged; replace it without exposing its original value or OID.
- [ ] Keep Counter64 rejected until the pinned upstream grammar supports it.

- [ ] Open a binary containing known SNMP OIDs: both text and tree show OSCAR
  names with instance indexes. Verify editing and save/reopen preserve bytes.
- [ ] Open an unknown numeric OID: preserve its unresolved suffix and encoded bytes.
- [ ] Open numeric source text: preserve the source while showing known names
  in the binary-derived tree.

- [ ] For each PacketCable version, select Snmp64 in Add and Edit: show OID,
  OSCAR Type dropdown, and Value; check hints, invalid input, prefilled values,
  and application of the edited statement. Covered by
  `MainWindowTest.packetCableSnmp64UsesStructuredAddAndEdit`.

## Windows DLL packaging

- [ ] Run portable DLL-layout tests, including relocation, collision rejection,
  unchanged plugins, and duplicate detection.
- [ ] On Windows CI, launch the prepared image and installed MSI without Java/Qt
  developer paths; verify root Qt DLLs exist and app/qt/lib has no DLL copies.
- [ ] Uninstall and verify that the launcher-side Qt DLLs are removed.

- [ ] Record final MSI download size and installed file totals separately; update
  release size notes with explicit MB/MiB units and label reference measurements.

- [ ] Fail a binary open with an existing edited document: retain that document,
  show the read-only diagnostic popup, and verify Copy report/Close.
- [ ] Verify first-error stopping, unknown entries, and candidate-family labeling;
  ensure reports omit payloads with obfuscation both enabled and disabled.
- [ ] Confirm text syntax and file-read errors use their ordinary error flow.

## Ubuntu in-app updates

- [ ] Verify installed Ubuntu amd64 offers Update only after a newer stable
  release has a unique DEB and usable SHA-256 metadata. Development/Windows,
  equal/older releases, malformed metadata, and missing hashes must not offer it.
- [ ] Verify the version, download size, and release link; changing latest during
  download must not change the selected installer.
- [ ] Exercise Save, Discard, Cancel, save failure, download cancellation, network
  timeout, truncated/oversized/corrupt download, and closing the download dialog.
- [ ] Verify administrator authentication is handled by Ubuntu, cancellation
  does not install, the helper waits for UI exit, and restart runs as normal user.
- [ ] Reject wrong package/architecture/version/hash, symlinks, and equal/older
  versions before APT; inspect the root-owned copy rather than a mutable download.
- [ ] Check APT lock timeout and installation failures show an actionable log
  location; do not claim automatic rollback after a partial package failure.
- [ ] Run Java service/native-dialog tests and `scripts/tests/test_ubuntu_update.py`.
- [ ] In Ubuntu 22.04/24.04/26.04 CI, upgrade a synthetic predecessor through the
  packaged installer helper, reject a repeat install, smoke-launch the result,
  preserve user configurations, and uninstall cleanly. This does not substitute
  for interactive polkit/cancellation testing on each desktop.

## Ubuntu dependency portability

- [ ] Run DEB normalization tests: preserve payload/other dependencies, reject
  direct PCRE1 linkage or missing GLib, and keep the original on failure.
- [ ] Verify the normalized DEB installs, launches, and removes on Ubuntu
  22.04, 24.04, and 26.04; a local synthetic DEB test is not this CI check.

## About uninstall

- [ ] Verify the button is hidden in development runs and present in installed
  Ubuntu/Windows packages; retain the uninstall-guide link.
- [ ] Verify confirmation Cancel and unsaved Save/Discard/Cancel, including save
  failure; helper-start failure must keep the UI open.
- [ ] Verify Ubuntu waits for UI exit, handles approval cancellation and APT
  failure with a log, removes successfully, and keeps external configurations.
- [ ] Verify Windows opens Installed apps and the registered uninstaller works.
- [ ] Run mocked helper tests; do not uninstall the developer's actual package
  as a routine check. Interactive polkit/Windows checks remain separate.

## CVC add/edit

- [ ] At the root, find ManufacturerCVC (32) and Co-signerCVC (33).
- [ ] Add each using DER file, PEM file/paste and HEX file/paste.
- [ ] Reject empty, odd-length/non-HEX and malformed PEM input.
- [ ] Verify a payload over 254 bytes remains one editable tree row.
- [ ] Add both CVCs; verify text and binary payloads retain every byte.
- [ ] Edit one CVC without changing the other; Cancel preserves the document.
- [ ] Verify selected-TLV hex includes all certificate fragments.
- [ ] Run CertificateInputTest, DocumentEditingTest and opt-in MainWindowTest.
- [ ] Verify native file selection on Ubuntu and Windows; synthetic byte tests
  do not establish certificate trust or modem interoperability.

## Upgrade indicator

- [ ] Newer stable release: show green Upgrade at the menu bar's upper-right.
- [ ] Equal/newer installed version, missing release or failed startup lookup: hide it.
- [ ] Show it for a newer release even without a DEB asset (including Windows).
- [ ] Click to open update options without automatically downloading/installing.
- [ ] Verify the Upgrade callback survives garbage collection and full-suite execution.
- [ ] Manual About checks refresh visibility; closing cancels pending checks.
- [ ] Verify light/dark appearance and keyboard focus.

## Windows patch prototype (pending)

- [ ] Run the read-only MSI baseline comparison on Windows; retain JSON evidence.
- [ ] Complete the [Windows update phase 1 checks](../architecture/windows-updates.md#1-prove-installer-compatibility-first) before enabling MSP updates.

- [ ] Run **Windows patch prototype** manually and retain its evidence artifact.
- [ ] Verify missing-product rejection, repeated patch, both repairs, patch removal/reapply and uninstall.
- [ ] Require exact payload hashes and matching About resource/Installed apps versions at every stage.
- [ ] Confirm native launch after every stage and unchanged external user files.
- [ ] Complete production baseline, interrupted rollback, downgrade and Windows 11 checks before release use.
