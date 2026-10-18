# Desktop releases

Each version has two uploaded application assets:

- `Oscar-Desktop-<version>-windows-x64.zip`: extract the whole ZIP and run
  `Oscar Desktop.exe`.
- `oscar-desktop_<version>_amd64.deb`: install with `sudo apt install ./<file>.deb`,
  then launch Oscar Desktop from the application menu.

Both include Java and Qt; the DEB also includes ICU and declares system display
library dependencies for APT. Windows includes app-local Visual C++ runtime DLLs.
GitHub adds its own **Source code (zip)** and **Source code (tar.gz)** links;
these are not application downloads and cannot be removed from the release UI.

Dependency source archives and their SHA-256 manifest are bundled inside each
application under `app/sources/` on Windows and
`/opt/oscar-desktop/lib/app/sources/` on Ubuntu. This increases package size but
keeps extra source archives and checksum files out of the release asset list.
Keep bundled license files and the Java runtime's `legal/` directory intact.

## Push the next release

Commit pending work first. From a clean checkout:

```bash
# Preview the default patch bump (0.1.0 -> 0.1.1).
tools/push-release.sh --dry-run

# Bump pom.xml, commit, create an annotated tag, and push.
tools/push-release.sh

# Alternatively choose a minor/major bump or explicit newer version.
tools/push-release.sh --push-patch  # default: 0.1.0 -> 0.1.1
tools/push-release.sh --push-minor  # 0.1.0 -> 0.2.0
tools/push-release.sh --push-major  # 0.1.0 -> 1.0.0
tools/push-release.sh --version 0.2.0
```

Run only the command for the release you intend. On Windows, the same helper is
available as `python tools/release/push-release.py` with the same options.

The helper checks the current branch against origin and rejects dirty trees,
detached HEAD, existing tags, and non-increasing versions. It changes only the
root application version in `pom.xml`; Oscar's dependency version stays separate.
It pushes the branch and tag atomically, without force. If a push fails, it
retains the local commit/tag and prints the exact retry command. Use that retry
instead of bumping again. Dry-run checks origin but does not edit, commit, tag,
or push. A failure after editing may leave local work to inspect; it never
resets or deletes user work.

For the original `v0.1.0` or an already prepared version, the Oscar-compatible
`tools/update-tag.sh --tag v0.1.0 --push` helper remains available. It does not
bump or commit; existing tags are never moved. Use a new version for new code.

## Independent platform workflows

- **Windows release** (`windows-release.yml`) builds and smoke-tests the native
  executable in a relocated directory with developer tools removed from PATH.
- **Ubuntu release** (`ubuntu-release.yml`) builds on Ubuntu 22.04 and checks
  installation and X11 launch under Xvfb on Ubuntu 22.04 and 24.04.

Both run on version-tag pushes and support manual dispatch. They upload only
their own application asset to the same draft release. Publishing steps serialize
per tag to avoid concurrent draft creation. Rerunning a platform job replaces
only that platform's asset. A manual branch run produces an Actions artifact
without creating a release. No local build is needed to release from CI.

After both workflows pass and both application assets appear, verify the downloads
and publish the draft. One platform may finish first; check both before publishing.
The helper triggers builds, not immediate public publication. Existing old release
assets are not deleted by these workflows.

## Build locally

Release packaging requires **Temurin 21.0.10+7**, Python, and the pinned Qt/Oscar
bootstrap dependencies. Source collection checks that the JDK matches the source
archive being distributed. Update the workflow JDK pins and
`scripts/collect-release-sources.py` together when changing Java versions.

Windows x64 also requires Visual Studio 2022 C++ build tools with the x64 CRT
redistributable directory. From PowerShell:

```powershell
./scripts/bootstrap-oscar.ps1
./scripts/setup-qt.ps1
./scripts/windows-release.ps1
```

On Ubuntu x64, install `fakeroot`, `dpkg-deb`, and the display libraries listed by
`install.sh`, then run:

```bash
./scripts/bootstrap-oscar.sh
./scripts/setup-qt.sh
./scripts/linux-release.sh
```

Outputs are in `dist/windows/release/` and `dist/linux/release/`. Move existing
`dist/windows/app-image` or `dist/linux/deb` output aside before rebuilding.
The Linux helper extracts and tests its DEB without installing system files.
The Windows ZIP does not need WiX. Plain `desktop.sh`/`desktop.ps1 package` is a
lower-level command; use the release helper to include corresponding sources.

## Verification

Before publishing, test Windows on a clean PC without Java, Qt, or Visual Studio:
extract into a path containing spaces, launch, open text/binary configurations,
edit/duplicate/delete TLVs, save, and reopen. Check themes and cancel/overwrite
prompts. CI's Windows host already has system runtimes, so its smoke test does
not establish clean-PC compatibility. Windows packages are currently unsigned.
Other Linux distributions, macOS desktop packaging, and ARM are not verified.

The source archives include upstream build instructions and notices. App-local
Microsoft DLLs remain subject to Visual Studio redistribution terms and need a
new application release when their runtime is updated.
