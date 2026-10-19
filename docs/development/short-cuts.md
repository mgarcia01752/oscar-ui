# Common CLI shortcuts

[Development guide](README.md) · [Git helpers](../../tools/git/README.md)

Examples use `/home/dev01/Projects/oscar-ui`; adjust it for another checkout.

## Build and run on the VM desktop

Log into the VM graphical desktop first. In your remote terminal, find the
current display number and the authorization path after `-auth`:

```bash
ps -u "$(id -un)" -o args= | grep '[X]wayland'
```

Replace `:0` and the `EXAMPLE` path below with those values, then run:

```bash
cd /home/dev01/Projects/oscar-ui &&
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
cd /home/dev01/Projects/oscar-ui &&
./scripts/unix/desktop.sh run
```

## Save changes with a commit message

Review changes first; the helper stages **all non-ignored changes**.
Replace the sample message and test result with what you actually changed.

```bash
cd /home/dev01/Projects/oscar-ui &&
git status --short &&
git diff --stat &&
git diff
```

```bash
cd /home/dev01/Projects/oscar-ui &&
tools/git/git-save.sh --commit-msg "Feature: Describe the change
- Describe the implementation
- Tests: State the checks performed"
```

## Push main only (no release)

```bash
cd /home/dev01/Projects/oscar-ui &&
tools/git/git-push.sh --master
```

## Push a release

Commit changes first. Choose **one** command: patch, minor, or major.
Each runs checks, bumps the version, commits, tags, and pushes. GitHub CI builds
and publishes the MSI and DEB only after the required checks pass.

```bash
cd /home/dev01/Projects/oscar-ui &&
tools/git/git-push.sh --tag-patch
```

```bash
cd /home/dev01/Projects/oscar-ui &&
tools/git/git-push.sh --tag-minor
```

```bash
cd /home/dev01/Projects/oscar-ui &&
tools/git/git-push.sh --tag-major
```

## Check release status

Requires authenticated GitHub CLI (`gh auth login`).

```bash
cd /home/dev01/Projects/oscar-ui &&
python3 tools/git/release-status.py
```

Append `--tag vX.Y.Z` to select a particular release, replacing `vX.Y.Z`
with its tag. The helper shows failed steps and log commands; pending is not
success. [Status helper details](../../tools/git/README.md#check-release-status).

## Update the OSCAR dependency

Replace `X.Y.Z` with the published OSCAR version, without a `v` prefix.
Wait until the upstream release JAR assets are available.

```bash
cd /home/dev01/Projects/oscar-ui &&
python3 tools/development/update-oscar.py --version X.Y.Z
```

This downloads the JAR, updates local references, and runs Maven verification.
Review and commit the changes, then rebuild the UI.
[Dependency update guide](build/oscar-dependency-updates.md).

## Build a local Ubuntu installer

```bash
cd /home/dev01/Projects/oscar-ui &&
python3 scripts/build/build-image.py --linux
```

This does not install, push, or publish. `--all` builds only the current OS;
Windows MSI builds require Windows. [Local installer guide](build/build-images.md).
