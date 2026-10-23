# Common CLI shortcuts

[Development guide](README.md) · [Git helpers](../../tools/git/README.md)

Examples use `~/Projects/oscar-ui`; `~` expands to your home directory in Bash.
Adjust the checkout location as needed. For quoted paths, use `"$HOME/Projects/oscar-ui"`
instead of `"~/Projects/oscar-ui"`, because quoted tildes do not expand.

## Build and run on the VM desktop

Log into the VM graphical desktop first. In your remote terminal, find the
current display number and the authorization path after `-auth`:

```bash
ps -u "$(id -un)" -o args= | grep '[X]wayland'
```

Replace `:0` and the `EXAMPLE` path below with those values, then run:

```bash
cd ~/Projects/oscar-ui &&
export DISPLAY=:0 &&
export XAUTHORITY="/run/user/1000/.mutter-Xwaylandauth.EXAMPLE" &&
xdpyinfo >/dev/null &&
./scripts/unix/desktop.sh --build-then-run
```

The authorization filename can change after logout or reboot. Use the same
Linux account as the desktop session. The window opens on the VM desktop.
If several sessions appear, get `printenv DISPLAY XAUTHORITY` from a terminal
inside the intended desktop instead. See [VM display troubleshooting](README.md#run-on-the-vm-desktop-from-a-remote-terminal).

To run again without rebuilding, in the same terminal with those variables set:

```bash
cd ~/Projects/oscar-ui &&
./scripts/unix/desktop.sh run
```

## Save changes with a commit message

Review changes first; the helper stages **all non-ignored changes**.
Replace the sample message and test result with what you actually changed.

```bash
cd ~/Projects/oscar-ui &&
git status --short &&
git diff --stat &&
git diff
```

```bash
cd ~/Projects/oscar-ui &&
tools/git/git-save.sh --commit-msg "Feature: Describe the change
- Describe the implementation
- Tests: State the checks performed"
```

## Push main only (no release)

```bash
cd ~/Projects/oscar-ui &&
tools/git/git-push.sh --master
```

## Push a release

Commit changes first. Choose **one** command: patch, minor, or major.
Each runs checks, bumps the version, commits, tags, and pushes. GitHub CI builds
and publishes the MSI and DEB only after the required checks pass.

```bash
cd ~/Projects/oscar-ui &&
tools/git/git-push.sh --tag-patch
```

```bash
cd ~/Projects/oscar-ui &&
tools/git/git-push.sh --tag-minor
```

```bash
cd ~/Projects/oscar-ui &&
tools/git/git-push.sh --tag-major
```

## Check release status

Requires authenticated GitHub CLI (`gh auth login`).

```bash
cd ~/Projects/oscar-ui &&
python3 tools/git/release-status.py
```

Append `--tag vX.Y.Z` to select a particular release, replacing `vX.Y.Z`
with its tag. The helper shows failed steps and log commands; pending is not
success. [Status helper details](../../tools/git/README.md#check-release-status).

## Update the OSCAR dependency

Resolve the latest published stable OSCAR release directly; no GitHub CLI is needed.
Wait until the upstream release JAR assets are available.

```bash
cd ~/Projects/oscar-ui &&
python3 tools/development/update-oscar.py --latest-jar
```

This downloads the JAR, updates local references, and runs Maven verification.
Review and commit the changes, then rebuild the UI.
[Dependency update guide](build/oscar-dependency-updates.md).

## Build a local Ubuntu installer

```bash
cd ~/Projects/oscar-ui &&
python3 scripts/build/build-image.py --linux
```

This does not install, push, or publish. `--all` builds only the current OS;
Windows MSI builds require Windows. [Local installer guide](build/build-images.md).

## Clean generated builds and installer images

Preview first, then remove `target/` and `dist/` (including old installer images):

```bash
cd ~/Projects/oscar-ui &&
./scripts/unix/clean.sh --dry-run
```

```bash
cd ~/Projects/oscar-ui &&
./scripts/unix/clean.sh
```

Use `--dist` for installers only or `--target` for build output only; either
can be combined with `--dry-run`. On Windows, use
`python tools/development/clean.py` with the same options.
Close running development instances and wait for builds to finish first.
These directories are removed in full, including test reports and generated
screenshots. Rebuild before running the development app again. Keep personal
files outside these output directories. Dependency caches (`.tools/`, `.m2/`),
configuration files, and tracked documentation screenshots are preserved.
Cleanup refuses tracked output files and symlinked output roots.
