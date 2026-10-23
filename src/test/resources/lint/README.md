# Negative text-lint fixtures

These synthetic files are deliberately invalid test inputs, not deployable
configurations. Use a temporary copy for editing, saving and repair-window tests.
Do not overwrite the canonical fixtures to make a test pass.

- `bad-config.txt`: 16 findings with OSCAR 3.7.0. Covers signed and unsigned
  numeric types and aliases, invalid IPv4 and hexadecimal values, unsupported
  escapes, missing quotes, unknown SNMP datatype, missing semicolon, nested
  ancestry, and missing closing brace. The final valid leaf remains accepted.
- `unterminated-comment.txt`: a separate EOF case because an unclosed comment
  prevents checking subsequent text. Reports the comment and missing root brace.
- `expected-diagnostics.json`: verified diagnostic codes and one-based positions,
  scan flags and leaf statuses for unit-test assertions. Generated from and
  inspected against OSCAR 3.7.0; do not regenerate automatically during tests.

`NetworkAccesss;` deliberately demonstrates the registry-name limitation: lint
accepts its syntax. Complete-file requirements, unknown registry names and MIB
constraints require separate validation. These examples cover representative
supported error categories, not every possible malformed input or resource limit.
The current API can report `fullyScanned=true` despite an unterminated comment;
this is an observation of existing behavior, not proof that all intended TLVs
were examined. Tests should inspect diagnostics as well as that flag.

## Read-only originals

Both text fixtures have local Unix mode `0444`. Git does not preserve write
permission bits, so restore that protection after a fresh checkout:

```bash
cd ~/Projects/oscar-ui &&
chmod a-w src/test/resources/lint/*.txt
```

For manual editing, copy to a temporary location and enable writing on the copy:

```bash
cp ~/Projects/oscar-ui/src/test/resources/lint/bad-config.txt /tmp/oscar-bad-config.txt &&
chmod u+w /tmp/oscar-bad-config.txt
```

Automated save/repair tests must copy classpath resource bytes into a JUnit
`@TempDir`, never save to `src/test/resources`. Read-only mode prevents ordinary
in-place writes; it is not immutable storage and does not prevent every editor
from replacing a file through its writable parent directory. Use Git diff to
verify canonical fixture contents remain unchanged.

Validation performed: both fixtures were checked with OSCAR 3.7.0's packaged
`--lint-text ... --lint-json` command; both returned exit code 1. `TextRepairServiceTest` now checks every recorded diagnostic position and leaf
status without modifying the fixtures. The repair-window tests use temporary copies.
