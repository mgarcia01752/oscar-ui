## Install OSCAR-UI

Choose the standalone package for your system. Java and Qt are included;
no repository checkout or separate Java/Qt installation is needed.

| Package | What is installed |
| --- | --- |
| Windows x64 `.msi` | OSCAR-UI, OSCAR engine, private Java runtime, Qt/Qt Jambi, app-local Visual C++ runtime DLLs, Start menu entry, and optional desktop shortcut |
| Ubuntu 22.04 / 24.04 x64 `.deb` | OSCAR-UI, OSCAR engine, private Java runtime, Qt/Qt Jambi, ICU, and application-menu launcher; APT installs required system display libraries |

**Both packages also contain license notices, dependency source archives, and
source checksums.** Source archives are not needed to run the UI, but are bundled
for source availability and contribute substantially to package size.

- **Windows:** run the MSI and launch OSCAR-UI from the Start menu.
  Uninstall through Settings → Apps → Installed apps → OSCAR-UI.
- **Ubuntu:** install the downloaded DEB with `sudo apt install ./oscar-ui_<version>_amd64.deb`
  (use the actual filename), then launch OSCAR-UI from the application menu.
  Uninstall with `sudo apt remove oscar-ui`.

Keep configurations outside the installation directory. Bundled runtimes are
private to OSCAR-UI. GitHub's **Source code** archives contain project source,
not runnable applications.
