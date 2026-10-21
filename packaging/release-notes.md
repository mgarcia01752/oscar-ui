## Install OSCAR-UI

Choose the standalone package for your system. Java and Qt are included; no repository checkout or separate Java/Qt installation is needed.

| Package | What is installed |
| --- | --- |
| Windows x64 `.msi` | OSCAR-UI, OSCAR engine, private Java runtime, Qt/Qt Jambi, app-local Visual C++ runtime DLLs, Start menu entry, and optional desktop shortcut |
| Ubuntu 22.04 / 24.04 / 26.04 x64 `.deb` | OSCAR-UI, OSCAR engine, private Java runtime, Qt/Qt Jambi, ICU, and application-menu launcher; APT installs required system display libraries |

## Included OSCAR engine

| Component | Bundled JAR size (approx.) | Included in |
| --- | ---: | --- |
| OSCAR engine | 0.54 MB | Windows and Ubuntu |

This is the OSCAR engine JAR only; its supporting libraries are also bundled. No separate OSCAR installation is needed.

## Included dependency source archives

| Dependency | Compressed source size (approx.) | Included in |
| --- | ---: | --- |
| OpenJDK 21.0.10+7 | 114.8 MB | Windows and Ubuntu |
| Qt 6.8.3 | 48.4 MB | Windows and Ubuntu |
| Qt Jambi 6.8.3 | 5.2 MB | Windows and Ubuntu |
| ICU 73.2 | 27 MB | Ubuntu |

These are **source archive sizes**, not runtime sizes or total installed sizes. The archives are bundled for source availability and contribute substantially to installer size. Users do not need to download or compile them separately. Both packages include license notices and source checksums.

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

Keep configurations outside the installation directory. Bundled runtimes are private to OSCAR-UI. GitHub's **Source code** archives contain project source, not runnable applications.

### Windows updates

The MSI establishes a patch baseline. Compatible subsequent releases can offer **Update on Windows** in About for the immediately previous version. Older installations without a compatible baseline, skipped versions and dependency changes require the full MSI. `windows-update.json` and `windows-patch-inputs.zip` support update and build automation; they are not standalone applications. An MSP appears only when compatibility and native patch lifecycle checks pass.
