## Install OSCAR-UI

Choose the standalone package for your system. Java and Qt are included;
no repository checkout or separate Java/Qt installation is needed.

| Package | What is installed |
| --- | --- |
| Windows x64 `.msi` | OSCAR-UI, OSCAR engine, private Java runtime, Qt/Qt Jambi, app-local Visual C++ runtime DLLs, Start menu entry, and optional desktop shortcut |
| Ubuntu 22.04 / 24.04 x64 `.deb` | OSCAR-UI, OSCAR engine, private Java runtime, Qt/Qt Jambi, ICU, and application-menu launcher; APT installs required system display libraries |

## Included dependency source archives

| Dependency | Compressed source size (approx.) | Included in |
| --- | ---: | --- |
| OpenJDK 21.0.10+7 | 110 MB | Windows and Ubuntu |
| Qt 6.8.3 | 46 MB | Windows and Ubuntu |
| Qt Jambi 6.8.3 | 5 MB | Windows and Ubuntu |
| ICU 73.2 | 27 MB | Ubuntu |

These are **source archive sizes**, not runtime sizes or total installed sizes.
The archives are bundled for source availability and contribute substantially
to installer size. Users do not need to download or compile them separately.
Both packages include license notices and source checksums.

## Installation instructions

[Windows installation guide](https://github.com/mgarcia01752/oscar-ui/blob/main/docs/install/windows.md)
· [Ubuntu installation guide](https://github.com/mgarcia01752/oscar-ui/blob/main/docs/install/ubuntu.md)

- **Windows:** run the MSI and launch OSCAR-UI from the Start menu.
  Uninstall through Settings → Apps → Installed apps → OSCAR-UI.
- **Ubuntu:** install the downloaded DEB with `sudo apt install ./oscar-ui_<version>_amd64.deb`
  (use the actual filename), then launch OSCAR-UI from the application menu.
  Uninstall with `sudo apt remove oscar-ui`.

Keep configurations outside the installation directory. Bundled runtimes are
private to OSCAR-UI. GitHub's **Source code** archives contain project source,
not runnable applications.
