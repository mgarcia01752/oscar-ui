# Install OSCAR-UI on Ubuntu

[README](../../README.md) · [Windows installation](windows.md)

Supports Ubuntu 22.04 and 24.04 on x64. Download the `.deb` from
[GitHub Releases](https://github.com/mgarcia01752/oscar-ui/releases).
Java and Qt are included; no repository checkout is needed.

## Install and launch

1. Download the Ubuntu `.deb` asset from the release into **Downloads**.
2. Open a terminal. Install using the commands below, replacing the filename
   if you downloaded a different version. The temporary copy lets APT read
   the package without access to your private Downloads folder.

   ```bash
   package_dir=$(mktemp -d /tmp/oscar-ui-install.XXXXXX)
   chmod 755 "$package_dir"
   install -m 644 "$HOME/Downloads/oscar-ui_0.2.2_amd64.deb" "$package_dir/"
   sudo apt install "$package_dir/oscar-ui_0.2.2_amd64.deb"
   rm -f "$package_dir/oscar-ui_0.2.2_amd64.deb"
   rmdir "$package_dir"
   ```

3. Confirm installation:

   ```bash
   dpkg-query -W -f='${Status} ${Version}\n' oscar-ui
   ```

   Expected for this example: `install ok installed 0.2.2`.
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

Close OSCAR-UI and download the newer DEB from
[GitHub Releases](https://github.com/mgarcia01752/oscar-ui/releases).
Install it using the same steps above, substituting the actual downloaded
filename. `sudo apt install ./<downloaded-filename>.deb` upgrades the installed
package; you do not need to uninstall first.

OSCAR-UI does not currently provide an APT repository. `sudo apt update` does
not discover new OSCAR-UI releases from GitHub.

## Uninstall

```bash
sudo apt remove oscar-ui
```

Keep configuration files in your own folders, outside the installation directory.
Uninstall removes the application and bundled runtime; those files remain.

Continue with the [user guide](../ui/README.md).
