# Tools: Check Shared Secret

[User guide](README.md)

Check a supplied key against an existing DOCSIS binary without opening it in
the editor. The check does not alter the file, current document or export key.

## Run the check

1. Choose **Tools → Check Shared Secret**.
2. Enter the **Shared secret**. The field is masked; hold the eye button to see
   it temporarily. Release the button to mask it again.
3. Click **Browse** and select the binary file, or enter its path.
4. Click **Check** and wait for the result.

A key and file path are required. The secret is cleared after checking or closing
the popup and is not saved in settings. You can use this tool with no open document.
Only binary input is checked; the tool does not compile text into a new binary.

## Results

| Result | Meaning |
| --- | --- |
| Correct Shared Secret | The supplied key matches all present supported keyed MICs, and CM integrity passes. |
| Incorrect Shared Secret | At least one keyed MIC differs from the calculation using the supplied key. |
| Unable to Check | The file cannot be read or verified: for example text, damaged CM integrity, missing MICs, unsupported algorithms or an oversized file. |

OSCAR checks the original bytes using compatible D3.1/D4.0 framing automatically.
Shared encodings do not uniquely identify the exact DOCSIS edition. Standard
and implicit/explicit extended MICs are supported with HMAC-MD5 or MMH.

A mismatch is not proof that the typed key alone is the problem: covered data or
the stored keyed digest may differ. A file using different legacy and extended
keys can also fail this single-key check. A passing result does not establish
full provisioning validity or modem interoperability.

See [Settings](settings.md) for the separate binary-export secret and
[Inspection](inspection.md) for hex and TLV views.
