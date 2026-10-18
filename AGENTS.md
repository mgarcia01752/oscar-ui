# Oscar UI agent instructions

These shared instructions apply to all agents working in this repository.

## Deferred work

Read [Development TODO](docs/development/TODO.md) before related work.
Keep deferred items and their verified blockers current. When resuming an item,
check its upstream dependencies and acceptance checks before implementing it;
mark it complete only after validation. Do not resume deferred work merely
because it appears in the list.

## Scope and implementation

- Oscar UI is a Java 21 / Qt Jambi desktop application. Read `pom.xml`,
  `docs/development/README.md`, and `docs/development/packaging.md` for dependencies and commands.
- Keep DOCSIS parsing, encoding, and registry ownership in upstream Oscar.
  Do not silently repair upstream TLV identifiers in UI display code. Report
  upstream defects and modify upstream only within the requested scope.
- The user also authors OSCAR. When investigating a suspected upstream defect,
  first distinguish OSCAR behavior from an OSCAR-UI integration defect. Report
  the evidence, affected upstream API/version, and user impact before adding
  a UI workaround or changing OSCAR. Let the user decide whether to fix OSCAR
  or accept a UI workaround; wait for that decision unless the session already
  explicitly authorizes the relevant fix. Continue independent investigation
  and tests while waiting. Fix confirmed UI-owned defects in OSCAR-UI.
- Preserve nested TLV structure, instance limits, and text/binary round trips.
  Never log shared secrets or real provisioning credentials; use synthetic
  fixtures. Do not describe hidden or masked settings as encrypted storage.
- Keep dependencies and tools project-local. Do not edit generated `.tools/`,
  `.m2/`, `target/`, or `dist/` as source or install system packages merely to
  validate a change.
- Keep architecture, development instructions, and deferred requirements in
  `docs/development/`; do not recreate a parallel root `development/` tree.
  `ui-architecture.md` owns current behavior, `checklist.md` owns repeatable
  verification, and `TODO.md` owns deferred work. Avoid duplicate checklists
  and stale prototype plans. User workflows belong in `docs/ui/`.
- Update these documents and their links when commands or behavior change.

## QA skill and validation

Use [coding-style](skills/coding-style/SKILL.md) when writing or reviewing
project-owned code. Every public method and public constructor requires a full
function documentation banner. Use braces and multiline bodies for Java
`if`, `else if`, and `else` branches; do not write single-line conditionals.
After fields and constructors, order methods by visibility: public, protected,
package-private, then private.
Use descriptive domain names and typed identifiers for map keys. Replace magic
protocol numbers with named definitions, checking upstream Oscar and the pinned
API before adding constants, codec lists, or other potentially redundant code.
Keep substantial panes and dialogs in focused classes, following
[UI component ownership](docs/development/ui-architecture.md).

Use [qa-sw-hygenie](skills/qa-sw-hygenie/SKILL.md) for software hygiene reviews
and validation of code, TLV handling, build tooling, or release changes.
Choose checks appropriate to the actual change; documentation-only changes
do not require a native application rebuild.

Use [qa-release](skills/qa-release/SKILL.md) for release readiness and changes
to configuration examples. Whenever files under `cm-config/examples/` are
added, changed, renamed, or removed, update `cm-config/README.md` in the same
change. Keep its example links, descriptions, units, and limitations current;
do not explain hidden or Git-ignored directories in that user-facing README.

Canonical skills live in `skills/<name>/SKILL.md`. Expose them through relative
directory symlinks in both `.agents/skills/` and `.claude/skills/`; do not copy
their contents. Validate the canonical path and both discovery paths after edits.

## Tools organization

- Keep Git, tag, commit, and release-push helpers and their tests under
  `tools/git/`. Entry points include `push-release.sh` and `update-tag.sh`;
  the cross-platform release implementation is `push-release.py` there.
- Keep developer maintenance helpers and their tests under `tools/development/`,
  including `update-oscar.py` for pinned dependency updates.
- Do not add loose wrappers at the root of `tools/` or duplicate implementations.
  Keep application build, bootstrap, and packaging scripts under `scripts/`:
  shell entry points in `unix/`, PowerShell in `windows/`, shared Python in
  `shared/`, and portable test harnesses in `tests/`. Keep `scripts/README.md`
  current when adding or moving a script. Unix does not imply macOS desktop
  support; only the OSCAR bootstrap is used by macOS CI.
- When moving a helper, update its project-root resolution, all script callers,
  test fixtures, CI commands, documentation, and skill references together.
  Run the relocated helper's tests from outside the checkout where supported;
  never push a real release just to validate a move.

## Push validation

Use `python3 tools/development/pre-push-checks.py` for the local push gate.
Enable its tracked hook with `python3 tools/git/install-hooks.py` in each clone;
never overwrite existing unrelated hooks or change global Git configuration.
Release helpers run preflight before version changes and the hook validates the
final committed revision before upload. Do not bypass failed checks to push.
Keep new check suites integrated with this gate and CI. Local success does not
replace Windows/Ubuntu installer jobs or prove untested platform behavior.

## Releases

- Preserve the two uploaded application assets: Windows MSI and Ubuntu DEB.
  Keep corresponding dependency sources and checksums inside packages.
  GitHub's automatic source archives are separate from these uploaded assets.
- Keep Ubuntu and Windows workflows compatible with the shared release draft.
  Read `docs/development/packaging.md` before changing packaging or release helpers.
- Version bump options are `--push-major`, `--push-minor`, and
  `--push-patch` (default). Release helpers commit, tag, and push; do not execute
  them as a routine validation step. Use temporary repositories for their tests.
- Do not claim Windows, Ubuntu, or native Qt validation from Java-only tests.
  Report checks run, skipped checks, and platform limitations accurately.

## Commit messages

For `commit-msg`, inspect `git status --short`, `git diff --stat`, `git diff`,
the staged diff, and all non-ignored untracked files. The helper stages all
changes, so describe the complete pending scope.

Return a copy/paste command using `tools/git/git-save.sh --commit-msg` with
a double-quoted multiline message; do not execute it unless asked to commit.
Use `Feature`, `BugFix`, or `Documentation` as the category, an imperative
one-line summary, `- ` detail lines, and a `- Tests:` line. Keep each message
line at most 60 characters. Never claim unperformed validation.
