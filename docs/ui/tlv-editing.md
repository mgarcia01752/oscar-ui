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
