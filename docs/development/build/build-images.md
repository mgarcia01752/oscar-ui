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

On Apple Silicon macOS, after the prerequisites below:

```bash
python3 scripts/build/build-image.py --macos
```

To select everything supported on the current host:

```bash
python3 scripts/build/build-image.py --all
```

Use `python` instead of `python3` on Windows. **`--all` builds only the current
OS installer**, not all three operating systems. Linux produces a DEB; Windows
produces an MSI; Apple Silicon macOS produces an experimental DMG.
Explicitly selecting another OS fails before building. Other ARM targets and
Intel macOS packaging are unsupported.

Preview the command and output path without starting a build:

```bash
python3 scripts/build/build-image.py --all --dry-run
```

Choose exactly one of `--linux`, `--windows`, `--macos`, or `--all`. An absolute path to
the script also works from outside the checkout. `--help` lists the options.

## Prerequisites

Use Python 3.10 or later and **Temurin Java 21** at the version in [the release pin](../../../scripts/build/release-java.json) for release packaging. Set
`JAVA_HOME` to that JDK and put its `bin` directory on `PATH`. The general Java
21 development requirement alone is insufficient for the pinned source bundle.
Prepare OSCAR and Qt using the [platform setup instructions](packaging.md#build-locally).

- Linux x64: Ubuntu 22.04 or 24.04, Bash, the development libraries, `fakeroot`,
  and `dpkg-dev`. Run `./install.sh --development --check` to inspect prerequisites.
- Windows x64: PowerShell 7 (`pwsh`), Visual Studio 2022 C++ build tools and
  x64 redistributable files, and WiX 3.14.1 on `PATH`.
- macOS ARM64: Apple Silicon (macOS 15 is the CI build platform), Xcode
  Command Line Tools, and the pinned Temurin Java 21 ARM64 JDK. Prepare the
  project-local OSCAR and Qt dependencies from the repository root:

  ```bash
  bash scripts/unix/bootstrap-oscar.sh
  bash scripts/macos/setup-qt.sh
  ```

  Packaging uses `macdeployqt`, `jpackage`, `codesign`, and `hdiutil`.
  Intel macOS and cross-building the DMG from Linux or Windows are unsupported.

The wrapper does not install prerequisites. Packaging downloads corresponding
dependency sources, so network access and disk space are required.

## Output and rebuilding

| Target | Local installer | Packaging helper |
| --- | --- | --- |
| Linux | `dist/linux/release/oscar-ui_amd64.deb` | `scripts/unix/linux-release.sh` |
| Windows | `dist/windows/release/OSCAR-UI-<version>-windows-x64.msi` | `scripts/windows/windows-release.ps1` |
| macOS ARM64 | `dist/macos/release/OSCAR-UI-<version>-macos-arm64.dmg` | `scripts/build/macos-release.py` |

The version comes from `pom.xml`. The wrapper refuses an existing destination
installer or intermediate build directory. Move previous output aside before
rebuilding: `dist/linux/deb` on Linux, or `dist/windows/app-image` and
`dist/windows/msi` on Windows, plus the same-version destination installer.
On macOS, move the existing `dist/macos` directory aside before rebuilding.
A failed build may leave partial output; inspect and move it aside before retrying.
Dependency caches are retained. The normal Maven build recreates `target/`.

The existing builders run their build, smoke, source/license, and packaging
checks. Linux checks the extracted DEB; Windows checks its application image.
macOS mounts the DMG, copies the app to a temporary location, and runs the
packaged application smoke check before removing that temporary copy.
MSI install/uninstall testing runs separately on disposable CI runners.
A successful local build does not replace the release CI requirements.

## Experimental Apple Silicon build

The DMG is ad-hoc signed, without an Apple Developer ID or notarization.
No Apple signing credentials are required. Gatekeeper approval on a downloaded
installer is a separate manual check; macOS automatic updates are not supported.
Native validation is CI-only when developing from Linux. See
[macOS packaging](packaging.md#experimental-macos-dmg) and the
[installation guide](../../install/macos.md).
