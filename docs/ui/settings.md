# Global settings and secrets

[User guide](README.md)

## Global

Open **Settings → Global** to choose:

| Option | Behavior |
| --- | --- |
| Appearance: System / Light / Dark | System is the default and follows the desktop theme reported by Qt. |
| Show CM/CMTS MIC TLVs | Off by default; exposes generated, read-only MIC entries in the tree. |
| Show TLV comments in configuration text | On by default; shows OSCAR's TLV path comments beside statements. |

Choose **OK** to apply or **Cancel** to retain the previous choices. Appearance
is saved between runs in the application's settings JSON. MIC visibility and
TLV-comment visibility are session choices.

## Secrets

Open **Settings → Secrets** to enter the shared secret used for binary export.
The field is masked. Hold the eye button to reveal its value; releasing it or
interrupting the hold masks it again. **Apply** retains the key for the current
session only. **Clear shared secret**, followed by Apply, clears it. Cancel
retains the previous session values.

**Obfuscate SNMP secrets** masks recognized community strings, SNMPv3 credentials
and security identities in the text, tree, editors, clipboard preview copies and
hex views. Text uses `********`; protected hex uses `FF FF FF FF`. These fixed
replacements do not disclose the original secret length. The status shows
**Masked** when enabled. This checkbox persists between runs.

To recognize an additional SNMP secret, enter an OSCAR-resolvable name or numeric
OID under **Custom SNMP secrets**, click **Add**, then **Apply**. Indexed instances
are included. **Remove** deletes a rule. Custom rules are session-only.
Some indexed symbolic SNMP names remain unsupported; use numeric OIDs when
OSCAR cannot resolve a name.

The shared secret and custom rules are not persisted or encrypted. Original
values remain in saved files regardless of masking. Read
[Configuration file security concerns](../configuration-file-security.md).

The [shared-secret checker](check-shared-secret.md) has its own temporary key
field and does not change the export key in Settings.
