# Build and packaging scripts

Run these commands from the repository root. See the
[development guide](../docs/development/README.md) for prerequisites and the
[packaging guide](../docs/development/packaging.md) for release validation.

| Directory | Purpose |
| --- | --- |
| `unix/` | Bash entry points; OSCAR bootstrap supports Linux/macOS, while Qt setup, desktop execution, and DEB packaging target Linux x64 |
| `windows/` | Windows x64 PowerShell setup, desktop, MSI packaging, and installer verification |
| `build/` | Python implementation shared by the platform entry points |
| `tests/` | Python packaging tests and the mocked Ubuntu development-installer test |

## Local installer wrapper

Use `python3 scripts/build/build-image.py --linux`, `--windows`, or `--all`
(`python` on Windows). `--all` selects the current host only. Add `--dry-run`
to preview. See [Build local installers](../docs/development/build-images.md)
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

Before pushing, use the shared [local validation gate](../docs/development/pre-push-checks.md).
The developer installer enables its Git hook; other checkouts can enable it
with `python3 tools/git/install-hooks.py`.

## Installed Ubuntu updater

`unix/ubuntu-update.py` is bundled in the Ubuntu app as `ubuntu-update.py`.
It is launched by the Update UI, waits for app exit, requests polkit approval,
validates a root-owned copy of the DEB, and uses APT to upgrade only OSCAR-UI and
required dependencies. It reports results through zenity and can reopen the app
as the original user. It is not a developer release/push script. See the
[architecture](../docs/development/ui-architecture.md#ubuntu-update-handoff).

`build/deb-dependencies.py` checks payload ELF dependencies and removes only
the build host's indirect PCRE1 requirement from generated DEBs, allowing
Ubuntu 26.04 to select its own GLib dependencies. See the
[dependency checks](../docs/development/packaging.md#ubuntu-cross-version-dependencies).
