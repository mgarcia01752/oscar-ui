# Install OSCAR-UI on Ubuntu

[README](../../README.md) · [Windows installation](windows.md)

Targets Ubuntu 22.04, 24.04, and 26.04 LTS on x64. Download the `.deb` from
[GitHub Releases](https://github.com/mgarcia01752/oscar-ui/releases).
Java and Qt are included; no repository checkout is needed.

## Install and launch

1. Open a terminal in the folder where you want to save the installer.
2. Download and install the latest published release:

   ```bash
   url=$(curl -fsSL https://api.github.com/repos/mgarcia01752/oscar-ui/releases/latest |
     python3 -c 'import json,sys; print(next(a["browser_download_url"] for a in json.load(sys.stdin)["assets"] if a["name"].endswith("_amd64.deb")))') &&
   curl -fL "$url" -o oscar-ui_amd64.deb &&
   sudo apt install ./oscar-ui_amd64.deb
   ```

   GitHub supplies the latest release's download URL, so no tag or version is
   needed. This works with both versioned and fixed DEB filenames. The `&&`
   operators run installation only after lookup and download succeed.
   If needed, install the download tools with `sudo apt install curl python3`.
   The downloaded DEB remains in the current folder and can be deleted after
   installation.

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
