# Build and packaging scripts

Run these commands from the repository root. See the
[development guide](../docs/development/README.md) for prerequisites and the
[packaging guide](../docs/development/packaging.md) for release validation.

| Directory | Purpose |
| --- | --- |
| `unix/` | Bash entry points; OSCAR bootstrap supports Linux/macOS, while Qt setup, desktop execution, and DEB packaging target Linux x64 |
| `windows/` | Windows x64 PowerShell setup, desktop, MSI packaging, and installer verification |
| `shared/` | Python implementation shared by the platform entry points |
| `tests/` | Python packaging tests and the mocked Ubuntu development-installer test |

## Linux

```bash
./scripts/unix/bootstrap-oscar.sh
./scripts/unix/setup-qt.sh
./scripts/unix/desktop.sh build
./scripts/unix/desktop.sh run
```

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

`shared/desktop.py` implements build, run, smoke, and packaging commands.
`shared/collect-release-sources.py` bundles dependency source archives and
checksums; release entry points invoke it after building the application.

```bash
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
bash scripts/tests/test-install.sh
```

The Bash test uses mocked prerequisites and temporary files; it does not install
system packages. Git/release helpers live in `tools/git/`, and the OSCAR version
updater lives in `tools/development/`.
