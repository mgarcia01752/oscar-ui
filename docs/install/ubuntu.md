# Install OSCAR-UI on Ubuntu

[README](../../README.md) · [Windows installation](windows.md)

Supports Ubuntu 22.04 and 24.04 on x64. Download the `.deb` from
[GitHub Releases](https://github.com/mgarcia01752/oscar-ui/releases).
Java and Qt are included; no repository checkout is needed.

## Install and launch

1. Download the Ubuntu `.deb` asset from the release into **Downloads**.
2. Open a terminal and paste this block. It selects the **most recently modified
   OSCAR-UI DEB in Downloads** (normally the latest download), prints its name,
   and installs only that file. This selects by download time, not version number.
   The temporary copy lets APT read the package without access to your private
   Downloads folder. Your downloaded files are kept.

   ```bash
   (
     set -euo pipefail
     downloads_dir="$HOME/Downloads"
     if command -v xdg-user-dir >/dev/null 2>&1; then
       downloads_dir=$(xdg-user-dir DOWNLOAD)
     fi
     shopt -s nullglob
     packages=("$downloads_dir"/oscar-ui_*_amd64*.deb)
     latest_package=""
     for candidate in "${packages[@]}"; do
       if [[ -f "$candidate" && ( -z "$latest_package" || "$candidate" -nt "$latest_package" ) ]]; then
         latest_package="$candidate"
       fi
     done
     if [[ -z "$latest_package" ]]; then
       echo "No OSCAR-UI amd64 DEB found in $downloads_dir. Download it first." >&2
       exit 1
     fi
     printf 'Installing: %s\n' "$latest_package"
     package_dir=$(mktemp -d /tmp/oscar-ui-install.XXXXXX)
     trap 'rm -f -- "$package_dir/oscar-ui.deb"; rmdir -- "$package_dir"' EXIT
     chmod 755 "$package_dir"
     install -m 644 "$latest_package" "$package_dir/oscar-ui.deb"
     sudo apt install "$package_dir/oscar-ui.deb"
   )
   ```

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

Close OSCAR-UI and download the newer DEB from
[GitHub Releases](https://github.com/mgarcia01752/oscar-ui/releases).
Run the same installation block above to select the latest download and upgrade
the installed package. Review the printed filename before confirming APT’s prompt;
if you recently downloaded an older release, that file will be selected instead.
You do not need to uninstall first.

OSCAR-UI does not currently provide an APT repository. `sudo apt update` does
not discover new OSCAR-UI releases from GitHub.

## Uninstall

```bash
sudo apt remove oscar-ui
```

Keep configuration files in your own folders, outside the installation directory.
Uninstall removes the application and bundled runtime; those files remain.

Continue with the [user guide](../ui/README.md).
