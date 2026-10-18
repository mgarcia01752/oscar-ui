---
name: qa-sw-hygenie
description: Review Oscar UI software hygiene and validate TLV editing, native UI, build tooling, and release packaging changes. Use for QA reviews or relevant implementation checks in this repository.
---

# Oscar UI software hygiene

## Inspect the change

Read root `AGENTS.md`, inspect tracked and staged diffs plus untracked files,
and identify affected behavior before selecting checks. Keep fixes scoped to
the request. Distinguish a confirmed defect from a proposed improvement.

For code changes, apply [coding-style](../coding-style/SKILL.md): check public
method documentation banners and reject single-line conditional statements.
Also check domain-specific names, meaningful map-key types, and named protocol
constants. Search Oscar for existing definitions before accepting duplicated
codec lists or mappings; distinguish UI editing restrictions from registry
support.

## Source license headers

Run `python3 tools/development/check-license-headers.py` for project-owned source.
Use `--fix` to add missing copyright/SPDX headers, then review the diff and rerun
without `--fix`. The script also runs in the local push gate and Java CI.
Use the copyright holder recorded in root `NOTICE` (OSCAR-UI contributors) and
`SPDX-License-Identifier: Apache-2.0`; retain existing years rather than updating
them on every run. The full license remains in root `LICENSE`.

Scope includes Java under `src/`, Python/shell/PowerShell under `scripts/` and
`tools/`, the installer, Git pre-push hook, and DEB maintainer scripts. Preserve
shebangs, encoding declarations, existing copyright notices, and third-party
licenses. Exclude Maven Wrapper, generated files, downloaded dependencies, and
vendor code. Different or incomplete existing notices require manual review;
do not relabel them automatically. Extend the explicit checker scope when new
project-owned source locations or languages are introduced.

## TLV and UI checks

- Machine TLV identifiers must not contain whitespace. Human-facing labels
  and string values may contain spaces; never strip those indiscriminately.
  Trace bad identifiers to the upstream Oscar registry rather than hiding
  defects through UI normalization. Add a focused regression test at the
  owning layer when changing registry validation.
- Check nested editing targets the selected child, duplication copies an
  independent subtree, and parent deletion confirms and removes descendants.
  Preserve ordering, instance limits, and dotted TLV paths.
- For file handling, verify loaded values survive edit/save/reopen, format
  detection and Save versus Save As behavior remain consistent, and cancellation
  preserves the document. Binary serialization must use Oscar.
- Keep secrets out of logs, exception messages, screenshots, and fixtures.
  Masking is not encryption. Check synthetic fixtures into tests only.

## Git helper documentation

When changing `tools/git/`, update `tools/git/README.md` in the same change if
helper inventory, CLI options, validation, side effects, or recovery behavior
changes. Check examples against `--help` and keep linked development docs in
sync. Test-only changes need a README edit only if documented coverage changes.

## Select validation

Run commands from the repository root. Use existing bootstraps documented in
`docs/development/README.md` when dependencies are missing; do not silently install system tools.

- Java/service changes: `./mvnw -B "-Dmaven.repo.local=$PWD/.m2" test`.
- Native UI changes: follow the Qt environment setup and opt-in test command
  in `docs/development/README.md`, then use `./scripts/unix/desktop.sh smoke` where supported.
  Ordinary Maven runs skip native UI tests; record that distinction.
- Git tag/release tooling: `python3 -m unittest discover -s tools/git -p 'test_*.py'`.
- Dependency updater: `python3 -m unittest discover -s tools/development -p 'test_*.py'`.
- Source bundling: `python3 -m unittest discover -s scripts/tests -p 'test_*.py'`.
- Shell changes: run `bash -n` and ShellCheck if available on changed scripts.
  Parse changed PowerShell with PowerShell when available.
- Workflow changes: use actionlint when available and inspect the matrix,
  artifact paths, permissions, and tag/version agreement.
- All changes: run `git diff --check` and review the final diff for accidental
  generated files, developer-specific paths, and unrelated changes.

Prefer behavioral regression tests for defects over tests that match source
wording. Documentation-only changes need link/path and consistency checks.

## Release-specific checks

Read `docs/development/packaging.md` and the affected workflow/helper. Confirm:

- Windows MSI and Ubuntu DEB include a usable launcher and runtime; users do
  not need the repository or a separately installed Java development kit.
- Verify install, launch, and uninstall in both platform workflows, including
  shortcut cleanup and preservation of user configurations outside install paths.
- Only those two application assets are uploaded. Dependency sources, notices,
  and checksums remain inside packages and match the bundled dependencies.
- Ubuntu install checks cover 22.04 and 24.04. macOS Java CI is not evidence of
  a working macOS desktop package.
- Release helpers reject dirty trees and conflicting tags, preserve existing
  tags, and push branch/tag atomically. Exercise them in temporary repositories,
  never by publishing a real release for testing.

Report findings with file locations and user impact, followed by actual check
results and remaining platform checks. Do not equate a successful build with
a successful native launch or an uploaded draft with a published release.
