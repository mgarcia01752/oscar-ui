---
name: qa-release
description: Check OSCAR-UI release readiness and keep the configuration example catalog accurate when examples or release documentation change.
---

# Release QA

Read root `AGENTS.md` and `docs/development/TODO.md`. For package or workflow
changes, use [software hygiene](../qa-sw-hygenie/SKILL.md) for the existing
platform checks and read `docs/development/packaging.md` for the release flow.
Use `docs/development/checklist.md` for repeatable verification and keep current
behavior in `ui-architecture.md` and deferred requirements in `TODO.md` in that
directory. Do not recreate local architecture/checklist copies under a root
`development/` directory. Do not run the push-release helper or publish as a
validation step.

## Configuration example catalog

- Compare tracked files under `cm-config/examples/` with `cm-config/README.md`.
  Add, update, rename, or remove catalog entries in the same change as examples.
- Read each affected example before describing it. Keep relative links,
  configured rates, burst units, enabled features, and limitations accurate.
- Use sanitized synthetic examples only. Distinguish masking fixtures from
  complete deployable SNMPv3 users or access policies; retain relevant cautions.
- Keep the README focused on finding and using examples. Do not describe
  hidden directories, Git-ignore behavior, or why a directory is tracked.
- Validate catalog links and coverage. When example contents change, also
  compile with the pinned OSCAR API using the project's documented workflow;
  report unsupported cases without silently changing upstream behavior.
  README-only changes need link and content checks, not an application rebuild.

## Release descriptions

Confirm `packaging/release-notes.md` and installer information describe the
actual bundled components. Keep Windows and Ubuntu installation links valid.
Report completed checks separately from platform checks still pending.

## OSCAR dependency updates

Use `python3 tools/development/update-oscar.py --version X.Y.Z` for a published upstream release;
see `docs/development/oscar-dependency-updates.md` for dry-run and recovery.
Check that Linux/Windows pins, Maven version, attribution cache, and current
release documentation agree. Preserve historical feature-version statements.
Run `python3 -m unittest discover -s tools/development -p 'test_*.py'` when
changing the updater. Its Maven verification is separate from native UI tests.

## Local push gate

Run `python3 tools/development/pre-push-checks.py` before uploading code, and
ensure `python3 tools/git/install-hooks.py` has enabled the checkout's pre-push
hook. Read `docs/development/pre-push-checks.md` for required validators, clean
revision requirements, and platform limits. Add new release checks to the gate
as well as CI; do not bypass a failure or treat native installer checks as local
passes when they only ran in CI. Test hook changes with temporary repositories.

For a scoped push, use `tools/git/git-push.sh --master` (current main/master only)
or `--tag TAG` (existing tag at HEAD matching `pom.xml`). The helper enables the
hook and runs checks during the push; those modes never bypass validation,
follow other tags, or create a new release version. For a new release, use
`--tag-major`, `--tag-minor`, or `--tag-patch` to delegate to `push-release.sh`;
these modes commit the version bump and push the branch and new tag together. Test it with disposable remotes.

Before expensive validation, detect no-op pushes against live origin refs.
Skip identical branch/tag uploads; reject conflicting tags and behind/diverged
branches. For release bumps, compare HEAD with the current version tag rather
than origin/main: already-pushed feature commits can still need a release.
Test that no-op paths do not run the checker or change versions/tags.

Keep `tools/git/README.md` synchronized with release/push helper changes in the
same change: inventory, CLI examples, checks, side effects, and retry behavior.

## User-facing Git commands

Start every copy/paste command block for Git activity with an executable `cd`
to the actual target repository's absolute path. This includes save/commit,
push, tag, release, and direct Git commands. A repository comment alone is not
enough. Verify the target checkout and its helper interface; do not assume the
current shell is already in the correct project. Use `cd /absolute/repo &&`
as the first line so a failed directory change prevents the following command.
Provide push/release commands for the user to execute unless explicitly asked
to execute them.
