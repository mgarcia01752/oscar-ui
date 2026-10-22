# Install OSCAR-UI on macOS (experimental)

[README](../../README.md) · [User guide](../ui/README.md)

The experimental DMG targets **Apple Silicon (M-series), macOS 15**. Intel
Macs are not supported by this package. Java and Qt are included.
Native packaging and launch must pass macOS CI before publication; interactive
Gatekeeper behavior still needs testing on a downloaded copy on a user's Mac.

## Install and launch

1. Open the [latest release](https://github.com/mgarcia01752/oscar-ui/releases/latest).
   Under **Assets**, download `OSCAR-UI-<version>-macos-arm64.dmg`.
   Choose the DMG disk image, not the **Source code** archives.
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

1. Download the new DMG from the
   [latest release](https://github.com/mgarcia01752/oscar-ui/releases/latest).
2. Quit OSCAR-UI, open the DMG, and drag **OSCAR-UI** into **Applications**.
3. Choose **Replace** when prompted, eject the disk image, and launch OSCAR-UI.
4. Check the installed version in **Help → About**.

## Uninstall

Quit OSCAR-UI and move it from Applications to Trash. Keep configuration files
outside the application bundle so replacing or removing the app leaves them
untouched.
