---
name: build-images
description: Build local OSCAR-UI installers with the shared build-image CLI; select native Linux or Windows targets, preserve existing output, and distinguish local packaging from publishing.
---

# Local installer builds

Read root `AGENTS.md` and `docs/development/build/build-images.md`. Use
`scripts/build/build-image.py` to orchestrate existing platform packaging;
do not duplicate source collection, runtime assembly, or release verification.

- Accept `--linux`, `--windows`, or `--all` as mutually exclusive targets.
  `--all` means all supported installers on the current host: one Ubuntu DEB
  on Linux x64, or one Windows MSI on Windows x64. Reject foreign targets,
  macOS, and ARM before invoking any builder. Do not dispatch GitHub Actions.
- Use `--dry-run` to inspect commands and paths without building. Resolve the
  checkout from the script location, not the caller's working directory.
- Check the pinned release JDK and platform prerequisites in the linked guide.
  Do not install system packages just to validate orchestration changes.
- Preserve existing output. Report directories to move aside; do not delete
  build output, source files, or dependency caches without authorization.
- Stream progress and propagate helper failures. Report success only when the
  expected installer exists and is nonempty. Do not bump versions, commit,
  tag, push, publish, or install the generated application.
- Use mocked orchestration tests for wrapper changes. Run the source-header
  checker and documentation checks. Record actual native builds separately;
  a dry-run is not a successful package build. Native install/uninstall checks
  remain on disposable CI runners.

Keep the README link, scripts inventory, build guide, and packaging guide
consistent with the CLI. Apply `qa-release` to changes in package contents.
