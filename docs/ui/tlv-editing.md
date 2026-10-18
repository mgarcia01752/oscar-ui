# Add, edit, duplicate and delete TLVs

[User guide](README.md)

## Add

Select the parent or document root, then choose **Edit → Add TLV** or **Add TLV**
from the tree's right-click menu. Search the catalog and choose an available entry.
The chooser has **TLV** and **Name** columns. Click a column heading to toggle
numeric path order or alphabetical name order. Paths use dots without brackets.

Adding a Parent TLV creates its immediate children as blank placeholders.
Nested parents stay empty until explicitly populated. Placeholders are not
recommended values or deployment defaults. Delete unwanted children and enter
supported values before compiling.

## Edit

Double-click a scalar TLV, or select **Edit → Edit TLV**. The popup contains the
current value, its data type, and an example or range. Apply changes the selected
instance; Cancel leaves it unchanged. Child TLVs are edited in the same way.

Parent TLVs hold child entries rather than a scalar value. Some unsupported
codecs are read-only, and generated CM/CMTS MICs cannot be edited manually.
For a masked secret value, leave the replacement field blank to retain the
existing value, or enter a replacement. See [Settings](settings.md#secrets).

## Duplicate

Select a top-level TLV and choose **Edit → Duplicate TLV** or its context-menu
command. This inserts a copy, including its children, immediately after the
original and selects the copy. For a service flow, edit the copied identifiers
and rates as needed; duplicating does not automatically assign unique values.
Catalog instance limits and protected entries still apply.

## Delete

Choose **Edit → Del TLV**, the context-menu delete command, or **Delete TLV**
in the edit popup. Confirm the deletion. Deleting a Parent TLV also removes all
its descendants. Protected entries remain read-only.

Changes remain in the document until you [save](files.md#save-and-save-as).
Use [View TLV Hex](inspection.md#selected-tlv-hex) to inspect a selected entry.

## SNMP fields

For **Snmp11** and PacketCable **Snmp64**, Add and Edit show the same three
fields:

| Field | Input |
| --- | --- |
| OID | Numeric OID or an OSCAR-resolvable name, including instance/index; for example `1.3.6.1.2.1.1.5.0` |
| Type | Choose from OSCAR's supported type list |
| Value | Enter the value without surrounding quotes; spaces in text are preserved |

The hint changes with Type: `IpAddress` expects an IPv4 address such as
`192.0.2.1`; `HexString` expects byte pairs such as `00:01:FF`; integer types
show their decimal range. Apply is enabled only after field checks and OSCAR
compilation succeed. OSCAR 3.2.0 lists Counter64 but cannot compile that type.

When an existing secret is masked, leave both OID and Value blank and keep
Type unchanged to retain it. To replace it, fill both fields. Cancel discards
changes.

Binary files display OSCAR-resolved SNMP names with their instance indexes in
both the tree and decoded text. Unresolved portions remain numeric; OSCAR may
return a partial name such as `iso.3.6...`. For text files, the source is
preserved and the tree shows OSCAR-resolved names.

PacketCable Snmp64 uses these same controls and validation. OSCAR 3.2.1 fixes
value export so projected IPv4 values retain their expected format. PacketCable
SNMP binary import remains unsupported; this fix does not remove that limit.
