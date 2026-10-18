# Desktop releases

Each version has two uploaded application assets:

- `OSCAR-UI-<version>-windows-x64.msi`: run the installer and launch
  OSCAR-UI from the Start menu.
- `oscar-ui_<version>_amd64.deb`: install with `sudo apt install ./<file>.deb`,
  then launch OSCAR-UI from the application menu.

Both include Java and Qt; the DEB also includes ICU and declares system display
library dependencies for APT. Windows includes app-local Visual C++ runtime DLLs.
GitHub adds its own **Source code (zip)** and **Source code (tar.gz)** links;
these are not application downloads and cannot be removed from the release UI.

Dependency source archives and their SHA-256 manifest are bundled inside each
application under `app/sources/` on Windows and
`/opt/oscar-ui/lib/app/sources/` on Ubuntu. This increases package size but
keeps extra source archives and checksum files out of the release asset list.
Keep bundled license files and the Java runtime's `legal/` directory intact.

### OpenJDK redistribution checks

Temurin is distributed under GPLv2 with the Classpath Exception. Preserve the
runtime's license, exception, copyright, and third-party notices, and distribute
its complete corresponding source, including build scripts. This project bundles
that source with the installer instead of relying on a written source offer.
There is no additional signed license form in this distribution approach.
See the [OpenJDK license](https://github.com/openjdk/jdk21u/blob/master/LICENSE).

`collect-release-sources.py` requires the pinned Temurin runtime version and
checks the JDK source archive against Adoptium's pinned SHA-256. Update that
checksum with the source URL and JDK pin when upgrading Java.
`verify-java-distribution.py` checks the extracted DEB, Windows application image,
and installed MSI payload before release upload. It verifies runtime/source
versions, GPL/Classpath text, module notices against the build JDK, source
checksum, and source/build-script presence. Identical shared notices are accepted
where DEB packaging omits duplicate per-module links. The check is distribution
QA, not a legal certification or a proof of reproducible JDK builds.

For an existing extracted package, run (paths shown for Ubuntu):

```bash
python3 scripts/build/verify-java-distribution.py \
  --runtime /path/to/extracted/opt/oscar-ui/lib/runtime \
  --sources /path/to/extracted/opt/oscar-ui/lib/app/sources \
  --jdk-home /path/to/matching-temurin-jdk
```

On Windows, use the installation's `runtime` and `app/sources` directories.
Omit `--jdk-home` only for a basic payload check; it skips comparison against the
original JDK notices and exact vendor/build identification.

The published v0.2.5 MSI and DEB were extracted and checked on Linux. Both contain
the GPL/Classpath text, corresponding JDK source/build files, and the source
archive matching Adoptium's SHA-256. Every distinct notice for the DEB's retained
modules also matches the original Temurin Linux JDK. Native Windows installation
and comparison with the Windows build JDK remain release-CI checks.


The MSI license dialog begins with the component summary from
`packaging/windows/install-information.txt`, followed by the unchanged project
license. Both release workflows use `packaging/release-notes.md` when creating
a new draft. Editing this template does not modify existing GitHub releases.
Keep these summaries aligned with the actual bundled components.

## Push the next release

Commit pending work first. From a clean checkout, the helper enables the
[pre-push hook](pre-push-checks.md) and runs local checks before version changes.
The hook checks the final release revision again before uploading. Missing
validators or failed checks block the push; CI still verifies both installers.

Run:

```bash
# Preview the default patch bump (0.1.0 -> 0.1.1).
tools/git/push-release.sh --dry-run

# Bump pom.xml, commit, create an annotated tag, and push.
tools/git/push-release.sh

# Alternatively choose a minor/major bump or explicit newer version.
tools/git/push-release.sh --push-patch  # default: 0.1.0 -> 0.1.1
tools/git/push-release.sh --push-minor  # 0.1.0 -> 0.2.0
tools/git/push-release.sh --push-major  # 0.1.0 -> 1.0.0
tools/git/push-release.sh --version 0.2.0
```

Run only the command for the release you intend. On Windows, the same helper is
available as `python tools/git/push-release.py` with the same options.

The helper checks the current branch against origin and rejects dirty trees,
detached HEAD, existing tags, and non-increasing versions. It changes only the
root application version in `pom.xml`; Oscar's dependency version stays separate.
It pushes the branch and tag atomically, without force. If a push fails, it
retains the local commit/tag and prints the exact retry command. Use that retry
instead of bumping again. Dry-run checks origin but does not edit, commit, tag,
or push. A failure after editing may leave local work to inspect; it never
resets or deletes user work.

For the original `v0.1.0` or an already prepared version, the Oscar-compatible
`tools/git/update-tag.sh --tag v0.1.0 --push` helper remains available. It does not
bump or commit; existing tags are never moved. Use a new version for new code.

## CI-gated platform workflows

- **Windows release** (`windows-release.yml`) builds the MSI from the complete application image, then verifies
  silent install, Start menu/desktop shortcuts, uninstaller registration, native
  launch without developer tools on PATH, and silent uninstall.
- **Ubuntu release** (`ubuntu-release.yml`) builds on Ubuntu 22.04 and checks
  installation, X11 launch under Xvfb, and APT removal on Ubuntu 22.04 and 24.04.
  Both platforms verify that a user configuration outside the installation
  directory survives removal. Installer logs are retained as CI artifacts.

`release.yml` handles version-tag pushes and manual runs selected on a tag.
It first calls `java-ci.yml` for the exact tagged commit, running the full
Ubuntu 22.04, Ubuntu 24.04 and macOS 15 matrix. Both platform jobs declare
`needs: ci`: failed, cancelled or skipped CI prevents packaging and uploads.
A passing branch build or an older tag does not satisfy this gate. The two
platform workflows are callable only through the coordinator, so direct tag
triggers or manual platform runs cannot bypass CI. Manual runs on branches
skip release jobs; choose the release tag when rerunning through Actions.

They upload only
their own application asset to the same draft release. Publishing steps serialize
per tag to avoid concurrent draft creation. Rerunning a platform job replaces
only that platform's asset. No local build is needed to release from CI.

After both platform workflows pass, the coordinator checks that the expected
Windows MSI and Ubuntu DEB assets are uploaded and nonempty, then automatically
publishes the release. A failed or skipped required job prevents publication;
any draft already created remains a draft. The push helper returns after pushing;
follow the Release workflow in GitHub Actions for the publication result.
The helper triggers this CI flow; it does not publish from the local machine.
Existing old release assets are not deleted by these workflows.

## Build locally

Use the [local installer wrapper](build-images.md) for `--linux`, `--windows`,
or host-only `--all`. The underlying platform commands are documented below.

Release packaging requires **Temurin 21.0.10+7**, Python, and the pinned Qt/Oscar
bootstrap dependencies. Source collection checks that the JDK matches the source
archive being distributed. Update the workflow JDK pins and
`scripts/build/collect-release-sources.py` together when changing Java versions.

Windows x64 also requires Visual Studio 2022 C++ build tools with the x64 CRT
redistributable directory, plus WiX 3.14.1 (`candle.exe` and `light.exe` on
PATH). CI configures WiX before packaging. From PowerShell:

```powershell
./scripts/windows/bootstrap-oscar.ps1
./scripts/windows/setup-qt.ps1
./scripts/windows/windows-release.ps1
```

On Ubuntu x64, run `./install.sh --development --check` and install the
reported missing packages, including `fakeroot` and `dpkg-dev`. Select the
pinned release JDK above, then run:

```bash
./scripts/unix/bootstrap-oscar.sh
./scripts/unix/setup-qt.sh
./scripts/unix/linux-release.sh
```

Outputs are in `dist/windows/release/` and `dist/linux/release/`. Move existing
`dist/windows/app-image`, `dist/windows/msi`, or `dist/linux/deb` output aside before rebuilding.
The Linux helper extracts and tests its DEB without installing system files.
The Windows MSI requires WiX 3; it wraps the prepared application image so
the app-local runtime DLLs remain included. Plain `desktop.sh`/`desktop.ps1 package` is a
lower-level command; use the release helper to include corresponding sources.

## Verification

Before publishing, test Windows on a clean PC without Java, Qt, or Visual Studio:
install into a path containing spaces, launch, open text/binary configurations,
edit/duplicate/delete TLVs, save, and reopen. Check themes and cancel/overwrite
prompts. CI's Windows host already has system runtimes, so its smoke test does
not establish clean-PC compatibility. Windows packages are currently unsigned.
Other Linux distributions, macOS desktop packaging, and ARM are not verified.

The source archives include upstream build instructions and notices. App-local
Microsoft DLLs remain subject to Visual Studio redistribution terms and need a
new application release when their runtime is updated.

## Application icons

The window and About dialog use the supplied light/dark square PNG icons from
`docs/images/`, bundled in the application JAR. Changing Appearance updates the
application icon. The supplied square light/dark files currently contain identical
artwork, so switching themes does not visibly change the icon. Desktop launchers use the fixed light variant: PNG for Ubuntu
and `oscar-ui.ico` for Windows. The ICO contains 16, 24, 32, 48, 64, 128, and
256 pixel sizes converted from `oscar-ui-light-icon.png`; regenerate it when
that source artwork changes. These assets are included in the normal packages,
not uploaded as additional release assets.

To regenerate the Windows icon on a development system with `python3-pil`:

```sh
python3 - <<'PY'
from PIL import Image
with Image.open("docs/images/oscar-ui-light-icon.png") as source:
    source.save("docs/images/oscar-ui.ico",
                sizes=[(size, size) for size in (16, 24, 32, 48, 64, 128, 256)])
PY
```

Run this from the repository root with the system Python that provides Pillow.
Normal builds use the committed ICO and do not run image conversion.

## Uninstall and upgrade behavior

Windows MSI installs machine-wide (administrator permission required) and registers
an uninstaller in Settings → Apps → Installed apps. The Start menu entry is
included; the installer offers a desktop shortcut. The stable upgrade UUID
`80186748-531f-4a39-a6da-f5c81a1f0730` identifies future versions of this product.
CI verifies fresh installation and removal; cross-version upgrades still need
validation with two released MSI versions. The old portable ZIP is not an MSI
installation and is not automatically removed.

Ubuntu removal uses `sudo apt remove oscar-ui`. CI checks that package-owned
files, including its desktop entry, are removed. Both uninstallers leave user
configuration files outside the installation directory intact. Do not store
personal files under Program Files or `/opt/oscar-ui`.

Only the MSI and DEB are uploaded as release assets. Windows installation tests
run only on disposable GitHub Actions runners; the local packaging script builds
and tests the image without installing the MSI on the developer's machine.

## Linux dock identity

Qt is initialized with the application name `OSCAR-UI` before any windows are
created. Its desktop-file ID is `oscar-ui-OSCAR-UI`, matching jpackage's generated
launcher filename. `packaging/linux/OSCAR-UI.desktop` adds
`StartupWMClass=OSCAR-UI` for X11 matching while retaining jpackage's substituted
launcher and icon paths. Ubuntu CI checks the installed entry's name and class;
the native smoke test checks the application's startup identity.

## DEB launcher registration

The DEB uses `packaging/linux/postinst` and `prerm` to copy
and remove its launcher at `/usr/share/applications/oscar-ui-OSCAR-UI.desktop`.
It does not invoke `xdg-desktop-menu`: minimal/headless Ubuntu systems may lack
its writable menu directories, which previously caused removal to fail with
exit status 3. The desktop entry retains its absolute installed icon path.
The installer uses separate `install -d` and `install -m` commands, supported
by GNU and BSD install, so isolated maintainer-script tests also run on macOS.
Removal errors are not suppressed; only the package's own launcher is removed.
CI checks that the registered launcher matches the bundled entry and disappears
after uninstall on Ubuntu 22.04 and 24.04, while user configurations survive.

Already-built v0.2.0 packages retain the old removal script. Publish the fix
under a new patch tag after validation; rerunning v0.2.0 builds the old code.
For an affected headless machine, creating the directory expected by xdg-utils
allows the old package to be removed:

```bash
sudo install -d -m 755 /usr/share/desktop-directories
sudo apt-get remove oscar-ui
```
