# OSCAR-UI user guide

[Project README](../../README.md) · [Configuration file security](../configuration-file-security.md)

Use this guide after [installing OSCAR-UI](../../README.md#download).
It describes the current source version; older installed releases may lack newer
features. The application is under development: successful editing or encoding
does not establish full configuration validity or modem interoperability.

## Features

| Task | Guide |
| --- | --- |
| Understand the tree, text preview and status | [Workspace](workspace.md) |
| Create, open, save or convert configurations | [Files](files.md) |
| Add, edit, duplicate or delete TLVs | [TLV editing](tlv-editing.md) |
| View binary hex and TLV breakdowns | [Inspection](inspection.md) |
| Choose appearance, show MICs and mask secrets | [Settings](settings.md) |
| Check a shared secret against a binary | [Tools: Check Shared Secret](check-shared-secret.md) |
| View package versions, licenses and available updates | [About and updates](about.md) |

## First configuration

1. Choose **File → Open** for an existing file, or **File → New** to start one.
2. Select a TLV in the configuration tree and double-click to edit its value.
3. Use **File → Save** to retain the current format, or **File → Save As** to
   choose Text or Binary.
4. For binary output, supply a shared secret when requested.

Opening a binary does not require a key and does not verify its MICs. Use
[Check Shared Secret](check-shared-secret.md) for a separate integrity/key check.

Developer setup, architecture, checklists and release workflows remain in the
[development documentation](../development/README.md).
