# Windows update plan

**Status: proposed; awaiting review. No Windows updater or MSP packaging is implemented.**

[Development guide](../README.md) · [UI architecture](ui-architecture.md#upgrade-chip) ·
[Packaging](../build/packaging.md#uninstall-and-upgrade-behavior) · [Deferred work](../todo/TODO.md#windows-updates)

## Objective and current behavior

Offer a small Windows update when only OSCAR-UI application code changes,
without downloading or replacing the bundled Java and Qt runtimes. Keep repair,
installed-version reporting and uninstall consistent with the updated application.

Today the green Upgrade chip and About can report a newer release on Windows;
the user follows the download link to the MSI. The Ubuntu installer handoff is
separate and remains unchanged by this proposal.

The current Windows build uses Java `jpackage` and WiX 3.14.1. It creates an
application image with a versioned `oscar-ui-<version>.jar`, then an MSI with a
stable upgrade UUID. That UUID alone does not establish patch compatibility:
ProductCode, component identities, file keys and installed paths must also be
examined. The existing release scripts do not create MSPs.

## Proposed update paths

| Installed state / release changes | Offered action |
| --- | --- |
| Supported baseline; only application payload changes; all dependencies match | Download and apply a Windows Installer MSP patch |
| Java, Qt, OSCAR, other runtime libraries, architecture or installer layout changes | Explain that a full installation is required; download the new MSI and guide the user through uninstall/reinstall |
| Older installation without a supported patch baseline | Full MSI installation instructions |
| Missing or inconsistent compatibility metadata | Full MSI instructions; never guess compatibility |
| No newer stable release | No Upgrade chip |

Select the path using explicit release compatibility metadata and the actual
installed package identity, not the major/minor/patch version number alone.
OSCAR is a separate dependency: moving it to 3.3.0 was not an application-only
change. Any future decision to include dependency changes in an MSP requires
its own compatibility and repair tests.

## Why not replace the JAR directly?

A direct replacement can run after the application exits, but MSI still knows
about the old installation. Repair can restore the original JAR or launcher
configuration, creating a downgrade or mixed installation. Whether ordinary
repair overwrites a particular unversioned file depends on the file rules;
forced repair can reinstall all packaged files.

Use an installer-managed patch so the updated files and Windows Installer's
registered package state move together. This must be proven with our generated
MSI; it is not a guarantee that current `jpackage` outputs are patch-compatible.

References: Microsoft [repair options](https://learn.microsoft.com/en-us/windows/win32/msi/reinstallmode),
[file versioning rules](https://learn.microsoft.com/en-us/windows/win32/msi/file-versioning-rules),
and [minor upgrades](https://learn.microsoft.com/en-us/windows/win32/msi/minor-upgrades).

## User flow

1. Click **Upgrade** or use About to check for updates.
2. Show installed/latest versions, download size, and either **Update and restart**
   or **Full installation required** with installation instructions.
3. For a patch, download to temporary storage without elevation. Verify artifact
   authenticity, checksum, release identity and compatibility before launch.
   A checksum from the same release detects corruption but is not an independent
   publisher-authenticity guarantee; select the signing approach in phase 1.
4. Ask the user to save or discard edits. Cancel or failed saving stops the handoff.
5. Launch a separate, narrowly scoped helper. Request Windows administrator
   approval when required by the installed package, then wait for OSCAR-UI to exit.
   Do not overwrite files still in use or terminate other Java applications.
6. Let Windows Installer apply the registered MSP. Capture its exit code and a
   diagnostic log that contains no configuration contents or shared secrets.
7. On success, restart OSCAR-UI under the original user's normal privileges.
   If Windows requires a reboot, report that explicitly instead of claiming
   the new application is running.
8. On cancellation/failure, report the outcome and preserve the usable installation
   through Windows Installer rollback where available. Do not remove installer
   caches or patch-registration data. Provide recovery instructions if rollback fails.

For the full-install path, help the user download the replacement MSI before
uninstalling. Keep personal configurations outside the installation directory.
The application must not silently remove itself or user files. Full reinstall
remains a user action; automatic MSI major-upgrade support is outside this plan.

## Packaging and compatibility contract

The release pipeline should publish a small manifest alongside the patch with:

- UI target version, Windows architecture, patch identity and download size/hash.
- Supported baseline ProductCode/ProductVersion and required prior patch state.
- A dependency fingerprint covering Java, Qt, OSCAR and other runtime libraries.
- Installer-layout compatibility version and permitted artifact source.

Validate the installed package through Windows Installer metadata and compare
its actual dependency manifest with the proposed patch. An About version string
alone is insufficient. Reject unsupported baselines and downgrades.

For patch-compatible releases, preserve component and file identities as required
by Windows Installer. Investigate the versioned JAR filename and generated launcher
configuration together. A stable application JAR name may simplify patching, but
changing existing installations to that layout may require a new full-MSI baseline.
Do not merely rename the JAR or assume the launcher will discover a replacement.

Retain the baseline/target installer inputs and symbols needed for reproducible
patch generation. Publish patches only for explicitly supported baselines. Define
whether one cumulative patch covers a baseline family or multiple patches are
needed after the prototype establishes what the tooling supports.

MSPs and compatibility metadata would add release assets beyond today's DEB/MSI
pair. Update the release policy, asset validation, documentation and CI together
only after approval. Measure actual patch sizes; binary JAR changes may still
require shipping the whole application JAR.

## Implementation phases and acceptance checks

### 1. Prove installer compatibility first

Build two synthetic application versions with identical dependencies. Inspect
ProductCode, UpgradeCode, component IDs, file keys and launcher configuration.
Prototype an MSP with the existing WiX toolchain; do not publish it.

Required evidence on Windows:

- Install baseline, apply patch, launch target; About and Windows Installed apps
  report the intended version.
- Compare Java/Qt/OSCAR files before and after: they must remain unchanged.
- Run normal repair and forced repair after patching. Confirm the target JAR,
  launcher and version remain consistent and the app still launches.
- Test patch sequencing, repeat application, unsupported baseline, downgrade
  rejection, custom install location and paths containing spaces.
- Uninstall the patched product and confirm package-owned files are removed,
  while user configurations/preferences follow the documented retention policy.
- Evaluate publisher signing and artifact-authentication requirements. Record
  unresolved constraints before enabling the updater.

If the existing MSI cannot support a reliable small patch, report the finding
and propose a new baseline or packaging change. Do not fall back to unmanaged
JAR replacement.

### 2. Implement the helper and UI handoff

After phase 1 approval, add release metadata parsing and the Windows-specific
update service/dialog. Test canceled elevation, blocked downloads, malformed
metadata, checksum mismatch, file locks, installer failure, reboot-required
results, rollback and normal-user restart. Reuse the shared version check and
unsaved-document flow without changing Ubuntu's working update behavior.

### 3. Add release CI and user documentation

CI must test baseline install → patch → launch → repair → launch → uninstall
before publishing patch assets. Include the full-reinstall fallback and show
supported baseline versions in release notes. Update the Windows installation
and About guides only when the behavior is available to users.

## Decisions for review

- Approve MSP as the application-only update path, subject to the prototype.
- Keep dependency/layout changes on the explicit full-uninstall/reinstall path.
- Approve investigating a new patch-compatible MSI baseline if existing releases
  cannot be patched reliably.
- Select signing and supported baseline/retention policy from prototype findings
  before implementation or publication.
