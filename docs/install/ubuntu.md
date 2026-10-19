# Install OSCAR-UI on Ubuntu

[README](../../README.md) · [Windows installation](windows.md)

Supports Ubuntu 22.04 and 24.04 on x64. Download the `.deb` from
[GitHub Releases](https://github.com/mgarcia01752/oscar-ui/releases).
Java and Qt are included; no repository checkout is needed.

## Install and launch

1. Open a terminal in the folder where you want to save the installer.
2. Download and install the latest published release:

   ```bash
   curl -fL -o oscar-ui_amd64.deb \
     https://github.com/mgarcia01752/oscar-ui/releases/latest/download/oscar-ui_amd64.deb

   sudo apt install ./oscar-ui_amd64.deb
   ```

   Run the install command only after the download succeeds. If `curl` is
   missing, install it with `sudo apt install curl` first. No tag or version
   number is needed. APT reads the package version from inside the DEB.
   The download remains in the current folder and can be deleted after installation.

   The fixed download name is available starting with the next release containing
   this change. For earlier releases, download the versioned DEB from
   [GitHub Releases](https://github.com/mgarcia01752/oscar-ui/releases) and pass
   that filename to `sudo apt install ./<downloaded-filename>.deb`.

3. Confirm installation:

   ```bash
   dpkg-query -W -f='${Status} ${Version}\n' oscar-ui
   ```

   Expected: `install ok installed` followed by the installed version.
4. Open the application menu, search for **OSCAR-UI**, and launch it.
   Alternatively, run this command as your normal user:

   ```bash
   /opt/oscar-ui/bin/OSCAR-UI
   ```

If you installed directly from Downloads and saw
`Download is performed unsandboxed as root ... _apt ... Permission denied`,
that notice alone does not mean installation failed. Confirm the status above;
if it reports `install ok installed`, no reinstall is needed.

## Update

Close OSCAR-UI and rerun the installation block above. It downloads the latest
published DEB and upgrades the installed package through APT. You do not need to
uninstall first. Review the version and changes shown by APT before confirming.

OSCAR-UI does not currently provide an APT repository. `sudo apt update` does
not discover new OSCAR-UI releases from GitHub.

## Uninstall

```bash
sudo apt remove oscar-ui
```

Keep configuration files in your own folders, outside the installation directory.
Uninstall removes the application and bundled runtime; those files remain.

Continue with the [user guide](../ui/README.md).
