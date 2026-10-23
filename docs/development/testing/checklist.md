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
| Native launch | `scripts/unix/desktop.sh smoke` | Qt startup, text menus and Settings icon popup, tree/text agreement, clipboard |
| Tooling | `tools/git`, `tools/development`, `scripts/tests` | Safe tags/pushes, verified dependency update/rollback, source bundling, DEB maintainer scripts |

For dependency-updater changes, test `--latest-jar` and `--version` selection,
dry-run forwarding, conflicting options, invalid latest tags and failed lookup.
Both selections must retain the existing checksum, rollback and downgrade checks.

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

- [ ] For Maven changes, verify wrapper checksum/version, resolved plugin
  dependencies, bootstrap installation and unchanged shipped dependency JARs.
  Run local service/native Qt tests, then the CI OS and installer matrix.
- [ ] For Java runtime updates, verify all release platforms have the pinned
  vendor binaries, the setup-java selector matches the runtime version, and
  source checksums match. Test with the actual selected JDK, verify source and
  legal files against a generated runtime, and check new-MSI baseline handling.

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
- [ ] Verify the green rounded-rectangle shape in light/dark themes and keyboard focus.

## GitHub Actions runtime

- [ ] Run OSCAR-UI CI and Windows/Ubuntu packaging with the Node.js 24 action
  versions; verify named artifact transfers and absence of Node.js 20 warnings.
- [ ] Verify tagged releases still require successful CI and package checks
  before publication.

## Windows MSP updates

- [ ] After release cleanup, verify a missing baseline starts a full-MSI family;
  authentication, rate-limit and server failures must still fail the build.
- [ ] Evaluate the real version confirmation prompt under strict PowerShell,
  including Windows PowerShell 5.1; manually verify confirmation and elevation.
- [ ] Verify Torch uses its build-local temporary-file compatibility setting,
  preserving installed tools and runtime options; pass native MSP generation.
- [ ] Verify the build step supplies `GH_TOKEN` for release metadata and patch
  chains; test authenticated/anonymous lookup without sending tokens to assets.
- [ ] Run Windows packaging tests with implicit-encoding warnings treated as
  errors locally, matching the Windows workflow before installer generation.
- [ ] Start with a read-only jpackage launcher; clear its build-image flag before
  preparing the previous image, preserving bytes, fingerprint and ProductCode.
  Require native Windows MSP generation and lifecycle checks afterward.
- [ ] Build successive versions with real jpackage launcher configurations;
  require a compatible MSP, not just successful standalone MSI builds.
- [ ] Check version-only launcher metadata is normalized while real Java/Qt
  option changes still require a new baseline; About reports the target JAR version.

- [ ] Verify publication resolves drafts through the authenticated release list
  and numeric ID; missing/ambiguous releases must fail before publication.

- [ ] Verify non-advertised Start menu/Desktop shortcuts have an HKCU registry
  key path and uninstall cleanup while the MSI remains per-machine.
- [ ] Verify branch CI stops after CodeQL and parallel OS checks. Tagged release
  jobs must also pass native WiX validation and MSI install/launch/uninstall.

- [ ] Check production UI property ownership: `WixUI_InstallDir` supplies
  `ARPNOMODIFY`; the standalone prototype retains its own copy. Require a
  successful native WiX link before claiming installer validation.

- [x] Isolated synthetic prototype passed hosted run `39350060186`.
- [x] Pass production MSI install/launch/uninstall with branding and shortcuts
  (run 39370659706 (historical run; deleted during cleanup)).
- [x] Pass real previous-release MSP, repeat application, normal/forced repair,
  exact file/version verification and uninstall in Windows CI
  (run 39368677913 (historical run; deleted during cleanup)).
- [ ] Pass original-baseline plus successive patch-chain lifecycle checks.
- [x] User confirmed a successful automatic Windows update from v0.4.13 to v0.4.14.
- [ ] On Windows 11, check Upgrade → About → Update on Windows, save/discard/cancel,
  download cancellation, UAC acceptance/cancellation and normal-user restart.
- [ ] Check network/hash failure, changed dependencies, unsupported/skipped versions,
  installer failure/rollback, locked files and reboot-required behavior.
- [ ] Confirm full-MSI fallback for incompatible installed baselines.
- [ ] Confirm metadata/MSP mismatch blocks release publication; compact inputs
  contain no duplicate runtimes and required earlier assets remain available.

See [Windows update architecture](../architecture/windows-updates.md).

### Embedded catalog discovery

- [ ] With the pinned OSCAR JAR, discover base document types without treating
  eRouter or OpenCable catalogs as standalone families.
- [ ] Open a DOCSIS configuration containing eRouter, nested eSTB VendorID,
  and an opaque eMTA payload; preserve the source and resolve tree entries.

- [ ] In an empty DOCSIS Add dialog, find eRouter and eSTB; in DOCSIS 4.0,
  find eRouter only. Verify eRouter child Add/Edit updates the text in both.
- [ ] In a regular DOCSIS reference template, confirm existing singleton parents
  are excluded from Add and the dialog explains instance-limit filtering.

- [ ] Add and edit nested eSTB Snmp11 through OID/Type/Value controls; verify
  validation uses the full parent path and rejects extra injected statements.

### OSCAR 3.5 integration

- [x] Use synthetic required service flows in finalized DOCSIS fixtures.
- [x] Retain independent CM/CMTS MIC byte comparisons and DPoE alignment checks.
- [x] Reject incomplete DOCSIS exports without changing existing destinations.
- [x] Verify repeated eRouter/eSTB parents remain available and duplicate correctly.
- [x] Dependency updater clean verify passed; Linux offscreen UI suite passed
  (133 tests, zero failures/errors, two opt-in screenshot tests skipped).
  Windows installer and interactive desktop validation remain separate.

### Add TLV catalog views

- [x] Default to Common TLVs; All TLVs exposes the complete parent catalog.
- [x] Keep instance-limited and generated entries visible but disabled with reasons.
- [x] Search uncommon entries, preserve retained selection/value on view changes,
  and disable Add for empty results.
- [x] Retain insertion-time limits independently of presentation filters.

### User-defined Common TLVs

- [x] Populate per-file-type top-level checkboxes from the registry.
- [x] Default only required user inputs; lock them and exclude generated MICs.
- [x] Retain draft choices across file-type switches; Cancel changes nothing.
- [x] Save/reload selections independently per type and preserve unknown entries.
- [x] Retain selections across registry edition changes; reject corrupt/future schemas without overwriting.
- [ ] Verify the same saved choices after an installed MSI/DEB update on each platform.
- [x] Capture the real dark-mode settings dialog for the user guide.
- [x] Linux offscreen suite: 139 tests, no failures/errors, three opt-in capture tests skipped;
  dedicated Common/Global/Add screenshot capture passed separately.

### Common TLV settings context regression

- [x] Reproduce the original wrong-family default (Docsis active, DPoE selected).
- [x] Exercise Global → Common settings → Save → Add through MainWindow,
  confirming optional selections appear and disappear without restarting.
- [x] Preserve saved preferences for other file types; do not migrate them by guesswork.


### Windows 11 release gate

- [ ] Require Java/tooling checks on Windows 11 ARM alongside Ubuntu and macOS.
- [ ] Confirm x64 JDK/Python selection on the ARM runner.
- [ ] Download the same candidate MSI built on Windows Server 2022; verify
  installation, native launch, shortcuts, removal, and user-file preservation.
- [ ] Retain Server MSI/MSP checks and all three Ubuntu installer checks.
- [ ] Confirm failed or cancelled required jobs block public release publication.
- [ ] Inspect Windows 11 installer logs; report CI execution separately from
  local workflow validation and interactive Windows 11 testing.

- [ ] Verify Help is last, About and update actions still work, and User Guide
  opens the GitHub docs/ui directory in the default browser.

- [ ] Confirm CodeQL runs before both OS test jobs and uploads Java results.
- [ ] Verify analysis failure prevents OS tests and release publication; review
  reported alerts separately from scan execution success.

### Experimental macOS package

- [x] Test positive derived bundle versions across pre-1.0 and 1.0 releases,
  preserving the displayed POM version and existing launcher metadata.
- [ ] Pass Apple Silicon macOS 15 build, framework deployment, and signature verification.
- [ ] Mount the generated DMG, copy and launch with build Qt hidden and environment cleared.
- [ ] Remove the test copy and preserve the external synthetic configuration.
- [ ] Confirm missing/failed macOS artifacts prevent public release publication.
- [ ] Test downloaded-app Gatekeeper approval on a user's Mac; do not equate CI with this check.
## Workspace layout controls

- [x] Verify Update is first, layout buttons are exclusive, and each mode shows
  the intended panes; returning to both restores previous splitter sizes.

## Template banner preference

- [ ] Verify Include template banner defaults to checked (SPDX and authors).
- [ ] Uncheck it, restart, and create All TLVs: omit the OSCAR banner.
- [ ] Cancel settings without changing the preference; re-enable for SPDX and authors.
- [ ] Confirm existing documents and TLV contents are unchanged.

## Multi-file workspace

- [x] Local native tests: mixed Text/Binary tabs and active tree/status sync.
- [x] Repeated TLV selection restoration, duplicate path reuse, and dirty isolation.
- [x] Background save ownership, failure retention, and open-destination collision.
- [x] Dirty close cancellation, all-document save review, and last-tab clearing.
- [x] Tab-row context menu reuses File actions, including an empty workspace.
- [ ] Confirm tab overflow, keyboard context menu, and desktop rendering on Windows/macOS.

### Qt dependency updates

- [ ] Change the POM Qt Jambi pin and verify matching Qt SDKs for every platform.
- [ ] Verify source archives, license notices and bundled ICU match native files.
- [ ] Test source-name propagation with two synthetic dependency versions.
- [ ] Run native dialog and launch checks on the oldest supported Ubuntu; run
  Windows/macOS and full installer checks in CI before publication.
- [ ] Review Qt advisories separately from the Maven dependency audit.

### Packaging text encoding

- [ ] Run packaging Python tests with `-X warn_default_encoding -W error::EncodingWarning`; preserve UTF-8 metadata and unrelated files through DEB normalization and maintainer scripts.

- [ ] Repeat strict packaging checks with CI Python 3.12 when developing on
  Python 3.10; subprocess encoding warnings differ between interpreter versions.

### Monthly dependency audit

- [ ] Check monthly scan findings and retained report; distinguish advisory matches
  from infrastructure failures. Review excluded native/runtime dependencies separately.
- [ ] Test pagination, malformed responses and failure reports without network calls.
