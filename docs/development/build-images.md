# Build local installers

Use the shared wrapper to build an installer from your current checkout.
It calls the existing platform packaging scripts, streams their progress, and
prints the resulting installer path. It does not change the version, push to
GitHub, publish a release, or install the application.

## Commands

From the repository root on Linux:

```bash
python3 scripts/build/build-image.py --linux
```

On Windows, from PowerShell:

```powershell
python scripts/build/build-image.py --windows
```

To select everything supported on the current host:

```bash
python3 scripts/build/build-image.py --all
```

Use `python` instead of `python3` on Windows. **`--all` builds only the current
OS installer**, not both operating systems. Linux produces a DEB; Windows
produces an MSI. Explicitly selecting the other OS fails before building.
macOS and ARM installer builds are unsupported.

Preview the command and output path without starting a build:

```bash
python3 scripts/build/build-image.py --all --dry-run
```

Choose exactly one of `--linux`, `--windows`, or `--all`. An absolute path to
the script also works from outside the checkout. `--help` lists the options.

## Prerequisites

Use Python 3.10 or later and **Temurin 21.0.10+7** for release packaging. Set
`JAVA_HOME` to that JDK and put its `bin` directory on `PATH`. The general Java
21 development requirement alone is insufficient for the pinned source bundle.
Prepare OSCAR and Qt using the [platform setup instructions](packaging.md#build-locally).

- Linux x64: Ubuntu 22.04 or 24.04, Bash, the development libraries, `fakeroot`,
  and `dpkg-dev`. Run `./install.sh --development --check` to inspect prerequisites.
- Windows x64: PowerShell 7 (`pwsh`), Visual Studio 2022 C++ build tools and
  x64 redistributable files, and WiX 3.14.1 on `PATH`.

The wrapper does not install prerequisites. Packaging downloads corresponding
dependency sources, so network access and disk space are required.

## Output and rebuilding

| Target | Local installer | Packaging helper |
| --- | --- | --- |
| Linux | `dist/linux/release/oscar-ui_<version>_amd64.deb` | `scripts/unix/linux-release.sh` |
| Windows | `dist/windows/release/OSCAR-UI-<version>-windows-x64.msi` | `scripts/windows/windows-release.ps1` |

The version comes from `pom.xml`. The wrapper refuses an existing destination
installer or intermediate build directory. Move previous output aside before
rebuilding: `dist/linux/deb` on Linux, or `dist/windows/app-image` and
`dist/windows/msi` on Windows, plus the same-version destination installer.
A failed build may leave partial output; inspect and move it aside before retrying.
Dependency caches are retained. The normal Maven build recreates `target/`.

The existing builders run their build, smoke, source/license, and packaging
checks. Linux checks the extracted DEB; Windows checks its application image.
MSI install/uninstall testing runs separately on disposable CI runners.
A successful local build does not replace the release CI requirements.
