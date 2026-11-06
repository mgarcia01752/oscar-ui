# Install OSCAR-UI on macOS (experimental)

[README](../../README.md) · [User guide](../ui/README.md)

Experimental DMGs target **macOS 15 on Apple Silicon (M-series) and Intel**.
Download the image matching your Mac; each includes its own Java and Qt runtime.
Native packaging and launch must pass macOS CI before publication; interactive
Gatekeeper behavior still needs testing on a downloaded copy on a user's Mac.

## Install and launch

1. Open the [latest release](https://github.com/mgarcia01752/oscar-ui/releases/latest).
   Under **Assets**, choose the DMG matching your Mac, not the **Source code** archives:

   | Mac | Disk image |
   | --- | --- |
   | Apple Silicon (M-series) | `oscar-ui-<version>-macos-arm64.dmg` |
   | Intel | `oscar-ui-<version>-macos-x64.dmg` |

   Check **Apple menu → About This Mac** if unsure which processor you have.
2. Open the DMG and drag **OSCAR-UI** onto **Applications**.
3. Eject the disk image and open OSCAR-UI from Applications.

This experimental build has an ad-hoc signature, not an Apple Developer ID
signature, and is not notarized. If macOS blocks it because the developer cannot
be verified, confirm you downloaded the intended project release, then follow
[Apple's instructions](https://support.apple.com/en-us/102445): attempt to open
it, then go to **System Settings → Privacy & Security → Open Anyway**.
Managed Macs may prevent this exception. Do not disable Gatekeeper globally.

## Update

The Update chip links to About and downloads; there is no automatic macOS
installer. To update manually:

1. Download the new DMG matching your Mac's architecture from the
   [latest release](https://github.com/mgarcia01752/oscar-ui/releases/latest).
2. Quit OSCAR-UI, open the DMG, and drag **OSCAR-UI** into **Applications**.
3. Choose **Replace** when prompted, eject the disk image, and launch OSCAR-UI.
4. Check the installed version in **Help → About**.

## Uninstall

Quit OSCAR-UI and move it from Applications to Trash. Keep configuration files
outside the application bundle so replacing or removing the app leaves them
untouched.
