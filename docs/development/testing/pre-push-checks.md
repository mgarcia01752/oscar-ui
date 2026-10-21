# Local pre-push checks

[Development guide](../README.md) · [Release packaging](../build/packaging.md) · [Checklist](checklist.md)

Every push from a configured checkout runs the same local validation gate,
including normal branches, tags, and release-helper pushes. A failure stops
uploading; fix the failure and retry. GitHub CI remains required because one
host cannot verify all operating systems or safely run installer removal tests.

## Push a branch or an existing tag

```bash
# Push only the current main/master branch (this repository uses main).
tools/git/git-push.sh --master

# Push only an existing tag at the checked-out commit.
tools/git/git-push.sh --tag v0.2.5
```

`--branch` is an alias for `--master`. Choose exactly one mode. The helper
requires a clean checkout and queries origin before starting build/test checks.
If the selected branch or tag already matches origin, it exits successfully with
“Nothing to push” and skips validation. Behind/diverged branches and conflicting
tags fail early. Otherwise it enables the local pre-push hook and lets that hook
run all checks before uploading. Failures stop the push. These two modes do not create
commits or tags, bump versions, force-push, or push other refs. Tag mode also
requires the tag to match `pom.xml`; replace the example with your actual tag.
Run from Git Bash on Windows with Python 3 available as `python3`.

A tag push can trigger release CI and publication. To bump a version and create
a new release, choose one of:

```bash
tools/git/git-push.sh --tag-major
tools/git/git-push.sh --tag-minor
tools/git/git-push.sh --tag-patch
```

These options delegate to `push-release.sh --push-major`, `--push-minor`, or
`--push-patch`. They run preflight checks, update the version, commit, create the
new tag, and push the branch and tag atomically. The pre-push hook checks the
final commit again. Before validation, release mode checks the current POM
version's tag: if it already publishes HEAD, it reports “Nothing to release”
and skips the bump and checks. If that tag is only local at HEAD, it stops and
asks you to push the existing release instead of creating another version.
Commits after that tag remain releasable even if already pushed to main.
There is no default bump and modes cannot be combined.
See [Release packaging](../build/packaging.md). Conflicting existing hooks are preserved;
resolve the reported conflict rather than bypassing checks.

## Enable once per checkout

```bash
python3 tools/git/install-hooks.py
```

On Windows use `python` if that is your Python command. This sets repository-local
`core.hooksPath` to `tools/git/hooks`; it does not change global Git configuration.
The installer refuses conflicting hook paths or existing active hooks rather
than overwriting or hiding them. Integrate existing hooks explicitly if needed.
Git does not install hooks when cloning: enable them in each new checkout.
`install.sh --development`, the release helper, `git-push.sh`, and `update-tag.sh --push` also
install this hook automatically. CI uses its own workflows rather than local hooks.

## Run checks directly

```bash
python3 tools/development/pre-push-checks.py
```

The standalone command checks your current working files, so it can run before
committing. It reports each stage, streams test/build output, and exits nonzero
on the first failure. It never commits, tags, or pushes. It downloads/verifies
the pinned OSCAR JAR, updates project-local dependency caches, and replaces
`target/` through Maven's clean build. It does not install system packages.

Required tools: Python 3.10+, Git/Bash, Java 21, Maven wrapper prerequisites,
the project's Qt setup on Linux/Windows, PowerShell 7 (`pwsh`), and `actionlint`.
PowerShell only parses scripts on other hosts; it does not execute Windows
installation commands. Place validators on PATH, or use these project-local paths:

- `.tools/release-validation/pwsh/pwsh` (`pwsh.exe` on Windows).
- `.tools/release-validation/actionlint/actionlint` (`actionlint.exe` on Windows).

A missing required validator blocks the gate with an actionable error; it is
not silently counted as passing. Use a native build of each tool for your host.
No validator downloads or elevated installations are performed automatically.

## Checks performed

| Check | Scope |
| --- | --- |
| Whitespace and UTF-8 | Working changes, maintained text files, Python syntax |
| Documentation | Local links/anchors and configuration-example catalog coverage |
| Dependency pins | Linux/Windows OSCAR pins and current-version references agree |
| Python suites | Git/release helpers, dependency updater and gate, source bundling, DEB scripts |
| Encoding regression | Development tooling and Windows packaging tests reject implicit text encodings, matching Windows CI |
| Shell | Bash syntax plus mocked Ubuntu developer-installer checks |
| PowerShell | Parse every platform script without running installer operations |
| Workflows | `actionlint` with optional ShellCheck/Pyflakes integration disabled |
| Dependency bootstrap | Download, checksum verification, embedded-POM installation |
| Java | Clean build and ordinary service tests |
| Native Linux/Windows | Qt smoke and opt-in dialog tests |

macOS runs Java and tooling checks; native macOS UI support is not implemented.
Windows/Ubuntu MSI/DEB packaging, installation/uninstallation, the other OS matrix
entries, and manual desktop verification remain CI/release checks. A successful
local gate does not promise that every CI job or device configuration will pass.

## Push behavior

The hook requires a clean working tree and checks that every nondeleted outgoing
branch/tag points to the checked-out commit. This prevents checking one revision
while sending another. To push a different revision, check it out and push it
separately. Deletion pushes still run the local gate. The hook rechecks the tree
and outgoing commits after validation to detect changes during the run.

The release helper first runs checks **before** editing the version, committing,
or tagging. A failure at this stage leaves those unchanged. After successful
preflight it creates the release commit/tag; the Git hook then checks that final
revision before the atomic upload. This second run covers the version change.
A failure after tagging retains the local commit/tag and prints the retry command.
`--dry-run` previews release selection only; it does not install hooks or run checks.

The tag helper uses the same hook when pushing. A hook failure can leave its new
local tag in place; no failing push is uploaded. Local hooks can be disabled by
Git configuration, so server-side CI remains the publication gate.

## Verify the gate itself

```bash
python3 -m unittest discover -s tools/git -p 'test_*.py'
python3 -X warn_default_encoding -W error::EncodingWarning -m unittest discover -s tools/development -p 'test_*.py'
```

Hook tests use temporary repositories and a fixture checker. They do not push to
GitHub or recursively invoke the full build. The runner removes inherited
repository-local Git environment variables before starting fixture tests.

Source headers are checked with `tools/development/check-license-headers.py`.
To add absent project-owned copyright/SPDX headers, run it with `--fix`, review
the changes, and commit them before pushing. Conflicting notices need manual
review; validation never rewrites source files.


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
