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
- Preserve nested TLV structure, instance limits, and text/binary round trips.
  Never log shared secrets or real provisioning credentials; use synthetic
  fixtures. Do not describe hidden or masked settings as encrypted storage.
- Keep dependencies and tools project-local. Do not edit generated `.tools/`,
  `.m2/`, `target/`, or `dist/` as source or install system packages merely to
  validate a change.
- Update relevant user documentation when commands or behavior change.
  `development/` is ignored local planning material, not shipped documentation.

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

Canonical skills live in `skills/<name>/SKILL.md`. Expose them through relative
directory symlinks in both `.agents/skills/` and `.claude/skills/`; do not copy
their contents. Validate the canonical path and both discovery paths after edits.

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
