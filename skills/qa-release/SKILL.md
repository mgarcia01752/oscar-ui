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
hook and runs checks during the push; it never bypasses validation, follows
other tags, or creates a new release version. Test it with disposable remotes.
