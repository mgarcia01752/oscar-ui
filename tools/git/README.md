# Git and release helpers

This directory owns local commits, checked pushes, release version bumps, and
Git hook setup for OSCAR-UI. Run the examples from the repository root. Bash,
Git, and Python 3 are required; on Windows use Git Bash with `python3` available.
See [local validation prerequisites](../../docs/development/testing/pre-push-checks.md)
for the tools needed by the full push gate.

## Common commands

### Commit changes locally

```bash
tools/git/git-save.sh --commit-msg "Feature: Describe the change
- Describe the implementation
- Tests: State the checks performed"
```

This stages **all non-ignored changes**, including new files and deletions, and
creates a local commit. Review `git status` and the diff first. It does not push.
Use `Feature`, `BugFix`, or `Documentation`; keep each message line within
60 characters and include actual validation results.

### Push only the main branch

```bash
tools/git/git-push.sh --master
```

This repository uses `main`. `--master` and its alias `--branch` push the current
`main` or `master` branch to origin, without pushing tags. Other branches and
detached HEAD are rejected in this mode. Commit or stash pending changes first.
If origin already has the commit, the helper skips checks and exits successfully.
Behind/diverged branches stop before expensive validation.

### Create and push a new release

Choose one command:

```bash
tools/git/git-push.sh --tag-patch
tools/git/git-push.sh --tag-minor
tools/git/git-push.sh --tag-major
```

These delegate to `push-release.sh`. Starting from a clean checkout, the release
helper checks origin, runs local preflight, updates `pom.xml`, commits the version
bump, creates an annotated tag, and atomically pushes the branch and tag. The
pre-push hook validates the final commit again. GitHub CI then builds and tests
the installers and publishes only after the required jobs pass.

If HEAD already matches the published tag for the current POM version, no new
release or checks are needed. A matching local-only tag stops another bump;
retry the existing release instead. Commits already pushed to main can still
need a release when they follow the current version tag.

The lower-level release helper also supports an explicit version and preview:

```bash
tools/git/push-release.sh --push-patch --dry-run
tools/git/push-release.sh --version 0.3.0 --dry-run
```

Replace the example version with a newer version. Preview contacts origin and
fetches branch metadata, but does not build, edit the version, commit, tag, or
push. Calling `push-release.sh` without options defaults to a real patch release;
`git-push.sh` requires an explicit mode.

### Push an existing tag

```bash
tools/git/git-push.sh --tag v0.2.5
```

Replace the example with your tag. It must already exist locally, point to HEAD,
and match the POM version. Only that tag is pushed. An identical remote tag skips
checks; a conflicting remote tag is never overwritten. This does not create a
tag or bump the version.

### Create a tag without bumping the version

```bash
tools/git/update-tag.sh --tag v0.2.5
```

This creates an annotated tag on HEAD if absent, without contacting origin.
Use it only when the committed version is already correct. Adding `--push`
checks origin and pushes a missing tag through the validation hook; remote-only
tags are fetched and conflicting tags are rejected. It does not edit `pom.xml`.
For normal new releases, prefer the release-bump commands above.

## Check release status

Requires GitHub CLI (`gh`) authenticated with `gh auth login`.
Show the newest release workflow (including queued or failed releases):

```bash
cd /home/dev01/Projects/oscar-ui &&
python3 tools/git/release-status.py
```

Select a specific tag:

```bash
cd /home/dev01/Projects/oscar-ui &&
python3 tools/git/release-status.py --tag v0.4.0
```

The helper reads the repository associated with this checkout, prints the run
URL and all job results from the latest attempt, and identifies failed steps
with log commands. It works outside the checkout directory too. It does not
push, cancel, rerun, or publish anything. Run it again to refresh; it reports
GitHub's current API state, which may be delayed during an incident.
Exit codes: 0 for success or pending, 1 for a completed unsuccessful workflow,
and 2 for no matching run or a query/tool error. A pending result is not a pass.

## Validation and failure recovery

```bash
python3 tools/git/install-hooks.py
python3 tools/development/pre-push-checks.py
```

The hook installer sets repository-local `core.hooksPath`; it preserves existing
unrelated hooks by refusing conflicts. Push helpers enable it automatically.
The shared checker runs source/header, script, documentation, workflow, Python,
Java, and supported native UI checks. See the
[validation guide](../../docs/development/testing/pre-push-checks.md) for exact coverage
and platform limits. Failures block uploading; do not bypass the hook.

If a release push fails after creating its commit/tag, those are retained.
Use the retry command printed by the release helper to push both refs without
another bump. Do not move published tags or blindly rerun a version bump.
Local checks do not replace native installer checks in release CI.

## Directory map

| File | Responsibility |
| --- | --- |
| `git-save.sh` | Stage all changes and commit locally |
| `git-push.sh` | Select branch, existing-tag, or release-bump push mode |
| `push-release.sh` | Bash entry point for the release implementation |
| `push-release.py` | Version calculation, preflight, commit/tag creation, atomic push |
| `update-tag.sh` | Create/check a tag, optionally synchronize it with origin |
| `release-status.py` | Read latest or tagged release progress and failed jobs |
| `install-hooks.py` | Enable the tracked hook for this checkout |
| `hooks/pre-push` | Git hook entry point |
| `pre-push.py` | Require a clean checkout matching pushed commits; run validation |
| `test_*.py` | Test helpers with temporary repositories and local bare remotes |

Build/package scripts belong in [`scripts/`](../../scripts/README.md).
Dependency updates and the shared validation runner belong in
`tools/development/`. Release behavior is documented in
[Release packaging](../../docs/development/build/packaging.md).

## Tests and maintenance

```bash
python3 -m unittest discover -s tools/git -p 'test_*.py'
```

Tests use disposable local remotes and fixture checkers; they do not push to
GitHub or create real releases. Validate shell changes with `bash -n` and update
behavioral tests when options or failure handling change.

**Keep this README current in the same change** whenever a helper is added,
removed, renamed, or its options, checks, side effects, or recovery steps change.
Update the directory map, command examples, and linked development docs together.


### Recheck an up-to-date branch

Use `tools/git/git-push.sh --master --force-push` to run local checks even
when origin already has the current commit. This option never passes Git's
force flag, overwrites tags, bypasses checks, or permits uncommitted changes.
The no-change message suggests this option; dirty checkouts still require
committing or stashing first.

For a version-only release, use
`tools/git/git-push.sh --tag-minor --force-push` (major/patch also supported).
The release helper creates a new version commit and tag, runs validation, and
uses a normal atomic push. Existing remote tags remain immutable.
