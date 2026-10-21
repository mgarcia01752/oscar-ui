# Install OSCAR-UI on macOS (experimental)

[README](../../README.md) · [User guide](../ui/README.md)

The experimental DMG targets **Apple Silicon (M-series), macOS 15**. Intel
Macs are not supported by this package. Java and Qt are included.
Native packaging and launch must pass macOS CI before publication; interactive
Gatekeeper behavior still needs testing on a downloaded copy on a user's Mac.

## Install and launch

1. Download `OSCAR-UI-<version>-macos-arm64.dmg` from
   [Releases](https://github.com/mgarcia01752/oscar-ui/releases), when available.
2. Open the DMG and drag **OSCAR-UI** onto **Applications**.
3. Eject the disk image and open OSCAR-UI from Applications.

This experimental build has an ad-hoc signature, not an Apple Developer ID
signature, and is not notarized. If macOS blocks it because the developer cannot
be verified, confirm you downloaded the intended project release, then follow
[Apple's instructions](https://support.apple.com/en-us/102445): attempt to open
it, then go to **System Settings → Privacy & Security → Open Anyway**.
Managed Macs may prevent this exception. Do not disable Gatekeeper globally.

## Update

Updates are manual: quit OSCAR-UI and replace the app in Applications with the
new version. The Update chip links to About and downloads; there is no automatic
macOS installer. To uninstall, quit and move OSCAR-UI from Applications to Trash.
Keep configuration files outside the application bundle; replacing or removing
the app must not remove your configuration files.
