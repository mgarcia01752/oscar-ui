# Install OSCAR-UI on Ubuntu

[README](../../README.md) · [Windows installation](windows.md)

Supports Ubuntu 22.04 and 24.04 on x64. Download the `.deb` from
[GitHub Releases](https://github.com/mgarcia01752/oscar-ui/releases).
Java and Qt are included; no repository checkout is needed.

## Install and launch

1. Ensure the download tools are installed (Java and Qt are already bundled):

   ```bash
   sudo apt install curl python3
   ```

2. Paste this block into a terminal. It finds the Ubuntu x64 DEB in GitHub's
   **latest published release**, downloads it, and asks APT to install it. No tag,
   filename, repository checkout, or GitHub account is needed. The temporary
   download is removed afterward, including when installation fails.

   ```bash
   (
     set -euo pipefail
     if [[ "$(dpkg --print-architecture)" != amd64 ]]; then
       echo "This installer requires Ubuntu x64 (amd64)." >&2
       exit 1
     fi
     package_dir=$(mktemp -d /tmp/oscar-ui-install.XXXXXX)
     trap 'rm -f -- "$package_dir/release.json" "$package_dir/oscar-ui.deb"; rmdir -- "$package_dir"' EXIT
     chmod 755 "$package_dir"
     echo "Checking the latest OSCAR-UI release..."
     curl --fail --location --retry 3 --silent --show-error \
       https://api.github.com/repos/mgarcia01752/oscar-ui/releases/latest \
       -o "$package_dir/release.json"
     package_url=$(python3 -c '
   import json, re, sys
   with open(sys.argv[1], encoding="utf-8") as source:
       release = json.load(source)
   assets = [asset for asset in release.get("assets", [])
             if re.fullmatch(r"oscar-ui_[^/]+_amd64\.deb", asset["name"])]
   if len(assets) != 1:
       sys.exit("Expected one Ubuntu amd64 DEB in the latest release. Try again after its build finishes.")
   url = assets[0]["browser_download_url"]
   if not url.startswith("https://github.com/mgarcia01752/oscar-ui/releases/download/"):
       sys.exit("Unexpected release download URL.")
   print("Downloading " + assets[0]["name"], file=sys.stderr)
   print(url)
   ' "$package_dir/release.json")
     curl --fail --location --retry 3 --show-error \
       "$package_url" -o "$package_dir/oscar-ui.deb"
     chmod 644 "$package_dir/oscar-ui.deb"
     echo "Installing OSCAR-UI..."
     sudo apt install "$package_dir/oscar-ui.deb"
   )
   ```

   GitHub's latest-release endpoint selects a published non-prerelease. The
   command stops if the release has no matching DEB, the API is unavailable or
   rate-limited, or the download fails; it will not install an older local file.

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
