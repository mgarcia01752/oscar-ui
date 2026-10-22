# Development TODO

## Python test encoding follow-up

- [x] Add explicit UTF-8 in DEB fixtures and dependency normalization.
  Preserve non-ASCII payload and metadata in regression tests; enforce strict
  encoding across the packaging suite in the local gate and Unix CI.

## Qt downloader follow-up

- [ ] Replace the pinned upstream aqtinstall fix with a published version that
  supports Qt 6.11+ Windows repository layouts. Verify Windows MSVC x64,
  Linux x64 and macOS downloads before changing the shared tool requirement.

## Java security follow-up

- [ ] Move the shared release Java pin to Temurin 21.0.13 once Windows x64,
  Linux x64, macOS ARM64 and matching source assets are all published. On
  2026-10-22, `jdk-21.0.13+9` had no Windows assets; the verified common release
  is `jdk-21.0.12.1+1`. This does not claim the October security fixes are included.
  Update `scripts/build/release-java.json`, verify the vendor source checksum,
  rerun source matching and native tests, and validate full installers in CI.

## Multi-file workspace

- [x] Implement [document tabs and synchronized workspace sessions](multi-file-workspace.md).
  Keep separate unsaved state, active TLV tree, and Text/Binary indicator per file.
  Use the supplied two-row filename-tab and directory-breadcrumb layout.


[Development guide](../README.md) · [Architecture](../architecture/ui-architecture.md) · [Verification checklist](../testing/checklist.md)

## Upstream status review

The current OSCAR pin is recorded in `pom.xml`. Several deferred investigations below were
recorded against older releases; their historical failures are not proof of a
current defect.

- [ ] Re-test indexed SNMP names, Counter64, PacketCable SNMP binary import and
  child TLV ordering against the pinned OSCAR release before resuming upstream fixes.

## VACM and SNMPv3 symbolic configuration

Status: deferred at the user's request. The investigation below was recorded
against OSCAR 3.1.2; recheck the currently pinned release before resuming.
Keep the working numeric OIDs in
`cm-config/examples/docsis/1gbps-ds-100mbps-us.txt` until this is resolved.

OSCAR 3.1.2 adds base-object mappings for USM, VACM, DH key-change, and community
objects. Its LUT lookup still requires an exact match. In the OSCAR-UI runtime,
the example's indexed symbolic names fail compilation with “Unknown object
identifier”: `usmUserSecurityName`, `usmUserAuthKeyChange`,
`usmUserPrivKeyChange`, and `vacmGroupName`, each followed by its numeric index.
The bundled upstream MIBs resolve these instances when Net-SNMP is explicitly
pointed at them; the LUT update alone does not provide portable instance lookup.

`vacmGroupName` has base OID `1.3.6.1.6.3.16.1.2.1.3`. Its example row suffix
is `.3.8.108.97.98.45.117.115.101.114`: security model 3 (USM), followed by the
length and ASCII bytes of `lab-user`. The assigned group is `lab-group`.
A symbolic column name does not remove the required row index.

- [ ] Fix indexed name/OID resolution in upstream OSCAR, preserving numeric
  instance suffixes and component boundaries. Do not add a duplicate UI LUT.
- [ ] Test both translation directions without an external Net-SNMP install,
  including optional leading dots and existing nonempty runtime caches.
- [ ] Publish the upstream fix and update OSCAR-UI's release/checksum pins.
- [ ] Replace the four example numeric object prefixes with symbolic names;
  preserve instance indexes, TLV order, and encoded bytes.
- [ ] Verify text compilation, binary tree projection, and fixed-length masking
  of values and identity-bearing indexes in text, tree, and hex views.
- [ ] Revisit a complete USM user and VACM access/view configuration separately.
  Current records are synthetic masking fixtures, not usable credentials or a
  complete access policy. A VACM group mapping alone grants no access.

## Binary export of SNMP configurations

- [x] Export DOCSIS SNMP configurations through OSCAR using original source
  values, independently of display masking. Validate save/reopen, repeated
  varbinds, compiled bytes, and destination preservation on compile failure.
- [ ] Support editable PacketCable binary import containing generated SNMP
  statements. That family-specific decoder limitation remains separate from
  DOCSIS export; unknown or lossy imports must still fail before saving.

## Configuration validation

Status: deferred requirements. Binary Save currently works directly through
OSCAR; optional Check Config is not a mandatory save gate. OSCAR now enforces
DOCSIS required top-level settings at finalization; broader family-specific
validation and UI presentation remain deferred.

- [ ] Define verified minimum rules per supported family/edition: required TLVs,
  nesting, instances, dependencies, encoded lengths, supported codecs, and
  required integrity/secret capabilities. Registry presence or successful
  encoding alone does not establish these rules.
- [ ] If a mandatory gate is authorized later, check and encode the same immutable
  document/type/secret snapshot off-thread; invalidate results when it changes.
  Keep stable check IDs, location, severity, and pass/fail/unverified results.
- [ ] Exercise stale checks, cancellation, missing secrets, encoder/write failures,
  and pending New/Open/Exit behavior without damaging files or document state.
- [ ] Extend independently verified complete-file and integrity fixtures beyond
  the limited adapter tests before making interoperability claims.

## Encrypted secret persistence

Status: deferred by request; requirements are not finalized. Shared-secret values
remain session-only. Display obfuscation and password fields are not encryption.
The former hashing-preference placeholder was replaced by display masking;
it is not a pending implementation of a selected hashing algorithm.

- [ ] Agree supported Linux/Windows environments, threat model, key ownership,
  credential scope, unlock behavior, and backup/migration/recovery policy.
- [ ] Evaluate Linux Secret Service and Windows Credential Manager as candidate
  backends behind a platform-independent service; no backend is selected yet.
- [ ] Define create/replace/clear/cancel and stored-status behavior. Untouched
  values should remain unchanged; failures must preserve previous credentials.
- [ ] Keep only credential references in ordinary preferences. Never fall back
  to plaintext when storage is missing/locked, or store a key beside ciphertext.
- [ ] Define secret consumption and explicit export behavior independently of
  encrypted local storage; verify OSCAR's input encoding without implicit trim.
- [ ] Test restart, locked/unavailable store, replacement, clear, migration,
  cancellation, and diagnostic secrecy on both platforms before enabling it.

## Additional editing scope

- [ ] Establish upstream/API and generic-adapter requirements for unsupported
  codecs and new families; do not invent per-TLV controls or duplicate registries.
- [ ] Keep DOCSIS 4.0 L2VPN and eDOCSIS/eSAFE editing deferred until requirements
  and verified support are available. Catalog presence is not sufficient.
- [ ] Keep certificate signing outside the current editor capability.

## Future Windows MSI size reduction

Status: deferred at the user's request. The current self-contained installer
works; keep its bundled Java runtime until a replacement is implemented and
validated. The user measured 282.7 MiB installed after DLL deduplication,
including a 76.4 MiB Java runtime.

- [ ] Evaluate an optional existing-Java installation mode. Detect and validate
  compatible 64-bit Java 21, and configure the launcher before omitting the
  private runtime; do not simply delete it after installation.
- [ ] Distinguish installed-size savings from MSI download savings. Keeping a
  fallback runtime inside the MSI does not reduce that download; evaluate the
  packaging approach separately before promising a smaller MSI.
- [ ] Define fallback behavior when compatible Java is absent, and recovery if
  external Java is later removed or changed. Keep the working bundled option.
- [ ] Never remove or modify shared Java when uninstalling OSCAR-UI. Explain
  external-runtime ownership and the dependency in the installer and user guide.
- [ ] Validate install, launch, upgrade, repair, and uninstall with and without
  compatible Java in Windows CI; measure actual MSI and installed sizes.

## Verification backlog

These items consolidate earlier unchecked prototype notes. Some have partial
fixture coverage; close them only with specific evidence, not because a feature
exists or an installer launches.

- [ ] Record a complete developer setup on a clean supported Ubuntu VM and a
  Windows development machine, separately from packaged-app installation.
- [ ] Record native Windows dialog/shortcut, theme, browser-link, and close/cancel
  checks; verify Ubuntu/Windows real system-theme notifications and both palettes.
- [ ] Broaden supported-codec and full-file independent fixtures, including
  family/version/length/instance metadata semantics and unsupported cases.
- [ ] Exercise compatible registry additions/removals/changes without UI edits.
- [ ] Inject template-export failures and verify current-document recovery.
- [ ] Expand edit/delete coverage for stale/protected selections, BOM/CRLF,
  quoted/numeric notation, selection retention, save/reopen, and instance slots.
  Existing focused tests do not establish exhaustive coverage.

## OSCAR SNMP Counter64 text input

OSCAR 3.2.0 `BERService.getDataTypeStringList()` includes Counter64, but its
text grammar does not accept it. Structured editor compile validation rejects
it; OSCAR-UI does not remove it from the upstream list or bypass compilation.

- [ ] Agree an upstream fix with the OSCAR author; reconcile the advertised
  types, grammar, and unsigned 64-bit encoding limits.
- [ ] Update the pinned release and test Counter64 boundaries before enabling
  acceptance; update the UI regression currently checking rejection.

## PacketCable Snmp64 value export

Resolved by OSCAR 3.2.1 and included in the current OSCAR dependency. The previous exporter converted
all two-byte-length varbind values to ASCII hex while retaining their BER type.

- [x] Remove the upstream length-dependent conversion. Upstream regressions
  cover six value types for Snmp11/Snmp64 across PacketCable 1.0, 1.5, and 2.0,
  including byte-preserving reimport.
- [x] Update the UI dependency and regression to require correct IPv4 prefill
  and valid Add/Edit replacement for each supported PacketCable header.
- [ ] PacketCable SNMP binary import remains deferred under Binary export of
  SNMP configurations above. Header/version handling is a separate limitation.

## TLV sibling reordering

Status: deferred at the user's request (2026-10-19). UI implementation drafted;
blocked by OSCAR 3.2.4 child ordering. Resume only when requested.
Do not release the draft until source, binary, and tree order agree.

The unfinished UI/service changes and regression tests are saved in
[deferred-tlv-reordering.patch](deferred-tlv-reordering.patch), outside active
source so they do not block unrelated dependency updates. They fail against
OSCAR 3.2.4 and 3.2.5 and are not release-ready. Upstream ordering has not been
modified. When resuming, review the patch and check it before applying:

```bash
cd /home/dev01/Projects/oscar-ui &&
git apply --check docs/development/todo/deferred-tlv-reordering.patch
```

- [ ] Trace dictionary storage and assembler traversal in OSCAR. Determine
  whether hash-based iteration loses insertion order or assembly follows
  registry order. Dictionary hashing is a hypothesis, not a confirmed cause;
  it is separate from MIC hashing. Do not assume all TLVs are numerically sorted.
- [ ] Establish any protocol-required ordering before proposing a change.
- [ ] Obtain approval for the upstream fix before modifying OSCAR.
- [ ] Verify MIC generation against the final encoded configuration using the
  applicable DOCSIS rules. Do not reorder signed bytes and retain old MICs;
  test generated integrity fields and shared-secret validation after export.
- [ ] Preserve child source order in OSCAR's text compiler. Synthetic reproducer:
  `Docsis { DownstreamServiceFlow { MaxSubstainedTrafficRate 1000; ServiceFlowReference 1; } }`.
  The current compiler emits child 25.1 before 25.8. The UI must continue to
  display binary order rather than hide this with a tree-only reorder.
- [ ] Finish Move Up/Move Down in Edit and tree context menus. Restrict moves
  to siblings, move complete subtrees, protect generated MICs, refresh both
  panels together, retain selection, and mark the document unsaved.
- [ ] Pass service and native UI regression tests, including nested boundaries,
  repeated instances, text and binary save/reopen, and masked values.
- [ ] Document the completed behavior in the user guide, architecture, and
  verification checklist after the upstream dependency is resolved.

## Windows updates

Status: production packaging and the interactive update success path are verified.
See [Windows updates](../architecture/windows-updates.md). Historical CI run IDs
below record evidence collected before workflow-history cleanup; those logs
were deliberately deleted.

- [x] Pass production baseline installation and real-code MSP CI. Run
  `39368677913` generated and applied the patch; run `39370659706` validated
  the corrected helper and MSI install/launch/uninstall.
- [x] Verify repeated patch application, normal/forced repair, file verification
  and uninstall in Windows CI (`39368677913`).
- [x] Record the user's successful automatic update from v0.4.13 to v0.4.14
  on their Windows PC. This establishes the interactive success path, not all
  cancellation and recovery cases.
- [ ] Validate Windows 11 UAC cancellation, download cancellation, unsaved-edit
  choices, installer failure/rollback, reboot handling and normal-user restart.
- [ ] Verify longer successive patch chains from the original baseline.
- [ ] Measure actual MSP size and document detailed Windows 11 results.
- [x] Validate v0.5.1 as the new full-MSI baseline after cleanup: release CI
  passed, retained inputs declare baselineVersion 0.5.1, and the user confirmed
  both Windows and Ubuntu installations.
- [ ] Validate the next compatible automatic update from v0.5.1 (planned v0.5.2).

Only the immediately previous compatible release is supported by the updater.
Changes to the installed helper or dependencies require a full MSI. Preserve
v0.5.1 and all subsequent releases in its patch family, including MSI/MSP,
metadata and patch inputs. v0.5.0 was deleted after v0.5.1 was validated.

## GitHub workflow and release cleanup

- [x] After validating v0.5.1, delete the v0.5.0 release and its two matching
  workflow runs; retain v0.5.1 assets and history.

- [x] Delete 11 releases older than v0.5.0 and 157 completed workflow runs;
  preserve v0.5.0 assets/runs, Git tags and source history.
- [x] Add and locally test full-MSI fallback when an old baseline release or MSI
  was removed; authentication, rate-limit and server failures still fail builds.
- [x] Retire the standalone Windows patch prototype workflow and its options;
  retain production patch validation and shared authoring helpers.
- [x] Rename Java CI to OSCAR-UI CI (`oscar-ui-ci.yml`) and update release calls,
  badges and documentation.

## GitHub Actions Node.js 24 migration

Status: completed and validated by the v0.5.1 release workflow.

- [x] Verify explicit Node.js 24 runtimes and update all workflow references:

  | Action | Previous | Updated |
  | --- | --- | --- |
  | `actions/checkout` | `v4` | `v5` |
  | `actions/setup-python` | `v5` | `v6` |
  | `actions/upload-artifact` | `v4` | `v6` |
  | `actions/download-artifact` | `v4` | `v7` |

  `actions/setup-java@v5` already uses Node.js 24.

- [x] Review runner requirements and artifact compatibility. Node.js 24 actions
  require Actions Runner 2.327.1 or newer. Workflows use GitHub-hosted runners.
  Downloads select artifacts by name; their destination behavior is unchanged.
  Artifact names, paths, retention and release gates remain unchanged.
- [x] Pass all required local checks, including workflow lint and documentation.
- [x] Verify OSCAR-UI CI and both installer workflows, artifact transfer and
  successful release publication in [run 39377086176](https://github.com/mgarcia01752/oscar-ui/actions/runs/39377086176).
  Its logs contain no Node.js 20 deprecation warnings.

This changes the actions' execution runtime, not OSCAR-UI's Java version or
bundled dependencies. See [CI runtime](../build/packaging.md#github-actions-runtime)
for upstream references.

## Embedded parent instance limits

Resolved in the OSCAR 3.5.0 dependency: regular DOCSIS eRouter (202)
and eSTB (217) support repeated parents. The UI follows the registry limits.

- [x] Consume the upstream parent multiplicity corrections.
- [x] Verify Add remains available and Duplicate creates another parent.

## DPoE embedded SNMP name

OSCAR 3.4.0 `dpoe.json` still declares 217.11 as `SnmpOID` with a one-byte
length field. Regular DOCSIS uses `Snmp11` in its OpenCable catalog. The UI
does not silently alias the DPoE name. The user authorized the upstream
correction: keep the specification/legacy terminology in the description and
use Snmp11 as the one-byte-length VarBind grammar identifier.

- [x] Verify the upstream DPoE VarBind name and nested value routing; both
  DPoE export routes pass independent-byte and round-trip tests locally.
- [ ] Publish the corrected OSCAR release and update the UI dependency.
- [ ] Test nested Add/Edit and round trips after updating the OSCAR dependency.

## Experimental macOS distribution

- [ ] Run the new macOS DMG workflow and resolve any native deployment failures.
- [ ] Verify interactive installation and Gatekeeper approval on a downloaded DMG.
- [ ] Consider Intel packaging, Developer ID/notarization, and automatic updates after Apple Silicon validation.
