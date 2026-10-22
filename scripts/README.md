# Build and packaging scripts

Run these commands from the repository root. See the
[development guide](../docs/development/README.md) for prerequisites and the
[packaging guide](../docs/development/build/packaging.md) for release validation.

| Directory | Purpose |
| --- | --- |
| `unix/` | Bash entry points; OSCAR bootstrap supports Linux/macOS, while Qt setup, desktop execution, and DEB packaging target Linux x64 |
| `windows/` | Windows x64 PowerShell setup, desktop, MSI packaging, and installer verification |
| `build/` | Python implementation shared by the platform entry points |
| `tests/` | Python packaging tests and the mocked Ubuntu development-installer test |

## Local installer wrapper

Use `python3 scripts/build/build-image.py --linux`, `--windows`, or `--all`
(`python` on Windows). `--all` selects the current host only. Add `--dry-run`
to preview. See [Build local installers](../docs/development/build/build-images.md)
for prerequisites, output paths, and rebuilding without overwriting output.

## Linux

```bash
./scripts/unix/bootstrap-oscar.sh
./scripts/unix/setup-qt.sh
./scripts/unix/desktop.sh build
./scripts/unix/desktop.sh run
```

For a single rebuild-and-launch command, use:

```bash
./scripts/unix/desktop.sh --build-then-run
```

It stops if the build fails and uses the current graphical display settings.

`desktop.sh smoke` runs the native UI smoke check. `linux-release.sh` builds and
checks the DEB without installing it on the developer machine. Ubuntu users
can also run the root `install.sh --development` to orchestrate setup.

## Windows

```powershell
./scripts/windows/bootstrap-oscar.ps1
./scripts/windows/setup-qt.ps1
./scripts/windows/desktop.ps1 build
./scripts/windows/desktop.ps1 run
```

`windows-release.ps1` builds the MSI and tests the application image.
`test-windows-install.ps1` installs, launches, and removes the MSI; it is intended
for disposable Windows CI runners, not routine checks on a developer machine.

`compare-msi-baselines.ps1 -Baseline PATH -Target PATH` reads two MSI databases
without installing them and reports identity/layout blockers. It is a prototype
audit, not a patch builder; see the [Windows update plan](../docs/development/architecture/windows-updates.md#production-patch-validation).

`test-windows-patch.ps1` builds and tests isolated synthetic baseline/target MSIs
and an MSP after the normal Windows build. It runs only on hosted Windows CI.
`build/windows-patch-prototype.py` prepares stable WiX inputs and verifies payload
hashes and application versions. Neither helper publishes or patches a production
installation. The standalone workflow is retired; see [production validation](../docs/development/architecture/windows-updates.md#production-patch-validation).

## Shared implementation and tests

`build/desktop.py` implements build, run, smoke, and packaging commands.
`build/collect-release-sources.py` bundles dependency source archives and
checksums; release entry points invoke it after building the application.
`build/verify-java-distribution.py` checks Java licenses, module notices,
matching source archives, and build files in extracted/installed packages.
The Ubuntu release and Windows image/install checks invoke it automatically.
`build/windows-dll-layout.py --image PATH` prepares a fresh Windows image with
one copy of the Qt DLLs beside the launcher; `--verify-only` checks an installed
image. The Windows release and MSI lifecycle scripts call it automatically.

```bash
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
bash scripts/tests/test-install.sh
```

The Bash test uses mocked prerequisites and temporary files; it does not install
system packages. Git/release helpers live in `tools/git/`, and the OSCAR version
updater lives in `tools/development/`.

Before pushing, use the shared [local validation gate](../docs/development/testing/pre-push-checks.md).
The developer installer enables its Git hook; other checkouts can enable it
with `python3 tools/git/install-hooks.py`.

## Installed Ubuntu updater

`unix/ubuntu-update.py` is bundled in the Ubuntu app as `ubuntu-update.py`.
It is launched by the Update UI, waits for app exit, requests polkit approval,
validates a root-owned copy of the DEB, and uses APT to upgrade only OSCAR-UI and
required dependencies. It reports results through zenity and can reopen the app
as the original user. It is not a developer release/push script. See the
[architecture](../docs/development/architecture/ui-architecture.md#ubuntu-update-handoff).

`build/deb-dependencies.py` checks payload ELF dependencies and removes only
the build host's indirect PCRE1 requirement from generated DEBs, allowing
Ubuntu 26.04 to select its own GLib dependencies. See the
[dependency checks](../docs/development/build/packaging.md#ubuntu-cross-version-dependencies).

`unix/ubuntu-uninstall.py` is bundled in the DEB for About's uninstall action.
It waits for the UI to exit, requests authorization for APT removal, and reports
the result without deleting external configurations or restarting the app.
Tests mock all removal processes; no real package removal is performed locally.

## Windows production updates

- `build/windows-installer.py`: author stable MSI/MSP inputs; retain compact
  previous payload/symbols; verify installed hashes.
- `windows/windows-msi.ps1`: build MSI and compatible MSP from verified release
  inputs, then run hosted production lifecycle checks.
- `windows/windows-update.ps1`: installed, user-confirmed MSP download/UAC/install
  helper with integrity and installed-baseline checks.
- `windows/test-windows-update-helper.ps1`: mocked compatibility/hash checks.
- `windows/test-production-patch.ps1`: hosted-only install/patch/repair/uninstall.
- `build/verify-windows-release.py`: publication gate for manifest/asset consistency.

See [Windows update architecture](../docs/development/architecture/windows-updates.md).

## Experimental macOS

`scripts/macos/setup-qt.sh` installs project-local Qt on Apple Silicon.
`scripts/build/macos-release.py` builds, ad-hoc signs, and verifies the DMG.
Use `python3 scripts/build/build-image.py --macos` after bootstrap/setup.
See [macOS packaging](../docs/development/build/packaging.md#experimental-macos-dmg).

## Clean development output

`./scripts/unix/clean.sh --dry-run` previews removal of `target/` and `dist/`.
Omit `--dry-run` to remove them, or select `--target` / `--dist` individually.
The wrapper resolves the repository from its own location. The shared helper
`python tools/development/clean.py` also works without Bash on Windows.
Dependencies and user configuration are preserved. See
[cleanup shortcuts](../docs/development/short-cuts.md#clean-generated-builds-and-installer-images).
