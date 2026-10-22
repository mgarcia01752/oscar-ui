## Install OSCAR-UI

Download the installer disk image or package under **Assets** on this release page.
For the newest version, visit the [latest release](https://github.com/mgarcia01752/oscar-ui/releases/latest).

Choose the standalone package for your system. Java and Qt are included; no repository checkout or separate Java/Qt installation is needed.

| Package | What is installed |
| --- | --- |
| Windows x64 `.msi` | OSCAR-UI, OSCAR engine, private Java runtime, Qt/Qt Jambi, app-local Visual C++ runtime DLLs, Start menu entry, and optional desktop shortcut |
| Ubuntu 22.04 / 24.04 / 26.04 x64 `.deb` | OSCAR-UI, OSCAR engine, private Java runtime, Qt/Qt Jambi, ICU, and application-menu launcher; APT installs required system display libraries |
| macOS 15 Apple Silicon `.dmg` (experimental) | OSCAR-UI application, OSCAR engine, private Java runtime, and Qt/Qt Jambi; drag the app into Applications |

## Included OSCAR engine

| Component | Bundled JAR size (approx.) | Included in |
| --- | ---: | --- |
| OSCAR engine | 0.54 MB | Windows, Ubuntu, and macOS |

This is the OSCAR engine JAR only; its supporting libraries are also bundled. No separate OSCAR installation is needed.

## Included dependency source archives

| Dependency | Compressed source size (approx.) | Included in |
| --- | ---: | --- |
| OpenJDK 21 (pinned Temurin release) | 115.1 MB | Windows, Ubuntu, and macOS |
| Qt 6.8.3 | 48.4 MB | Windows, Ubuntu, and macOS |
| Qt Jambi 6.8.3 | 5.2 MB | Windows, Ubuntu, and macOS |
| ICU 73.2 | 27 MB | Ubuntu |

These are **source archive sizes**, not runtime sizes or total installed sizes. The archives are bundled for source availability and contribute substantially to installer size. Users do not need to download or compile them separately. All packages include license notices and source checksums.

## Package size

Windows packaging keeps one copy of the Qt DLLs beside the launcher. Applied to the v0.3.0 payload, this removes **21.9 MB (20.93 MiB)** of duplicate files; Java and dependency source archives remain included.

Download size is shown beside each release asset. It differs from the installed file size, and Windows **Installed apps** may show a different estimate. The v0.3.0 file payload measured 318.4 MB; applying only DLL deduplication reduced that extracted payload to 296.4 MB. These are reference measurements, not a size guarantee for this release or a measurement of a newly built MSI.

MB uses decimal bytes; MiB uses binary bytes. See the [Windows installation guide](https://github.com/mgarcia01752/oscar-ui/blob/main/docs/install/windows.md#installation-size).

## Installation instructions

### Windows

1. Download and run the **Windows x64 MSI** from this release.
2. Launch **OSCAR-UI** from the Start menu.

If Windows displays **Windows protected your PC**, confirm the MSI came from this project, then follow the [PowerShell installation steps](https://github.com/mgarcia01752/oscar-ui/blob/main/docs/install/windows.md#powershell). Complete any Windows approval prompts; these steps do not disable SmartScreen.

**Uninstall:** Settings → Apps → Installed apps → OSCAR-UI → Uninstall.

[Windows installation guide](https://github.com/mgarcia01752/oscar-ui/blob/main/docs/install/windows.md)

### Ubuntu

1. Download the **Ubuntu x64 DEB** from this release.
2. Open a terminal in the download folder and install it using its actual filename:

   ```bash
   sudo apt install ./oscar-ui_amd64.deb
   ```

3. Launch **OSCAR-UI** from the application menu.

**Uninstall:**

```bash
sudo apt remove oscar-ui
```

[Ubuntu installation guide](https://github.com/mgarcia01752/oscar-ui/blob/main/docs/install/ubuntu.md)

### macOS (Apple Silicon, experimental)

1. Under **Assets**, download `OSCAR-UI-<version>-macos-arm64.dmg`.
2. Open the DMG and drag **OSCAR-UI** onto **Applications**.
3. Eject the disk image and launch OSCAR-UI from Applications.

The package targets macOS 15 on M-series Macs; Intel Macs are not supported. Java and Qt are included. It is ad-hoc signed, not Developer ID signed or notarized. If macOS blocks launch because the developer cannot be verified, confirm the download source and follow the [Gatekeeper steps in the macOS installation guide](https://github.com/mgarcia01752/oscar-ui/blob/main/docs/install/macos.md#install-and-launch). Interactive downloaded-app testing remains pending.

**Update:** Quit OSCAR-UI and replace the app in Applications using the new DMG. macOS updates are manual.

**Uninstall:** Quit OSCAR-UI and move the app from Applications to Trash.

[macOS installation guide](https://github.com/mgarcia01752/oscar-ui/blob/main/docs/install/macos.md)

Keep configurations outside the installation directory. Bundled runtimes are private to OSCAR-UI. GitHub's **Source code** archives contain project source, not runnable applications.

### Windows updates

The MSI establishes a patch baseline. Compatible subsequent releases can offer **Update on Windows** in About for the immediately previous version. Older installations without a compatible baseline, skipped versions and dependency changes require the full MSI. `windows-update.json` and `windows-patch-inputs.zip` support update and build automation; they are not standalone applications. An MSP appears only when compatibility and native patch lifecycle checks pass.
