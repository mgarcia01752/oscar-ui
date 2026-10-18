# Configuration file security concerns

[Back to OSCAR-UI](../README.md)

OSCAR-UI is a configuration editor, not a secure storage system. It does not
protect configuration files containing sensitive information. Users are
responsible for protecting those files, their copies, and the systems on which
they are stored or shared.

## Sensitive configuration values

Text configurations can contain readable SNMP community strings, passwords,
SNMPv3 security identities, key-change values, and vendor-specific credentials.
Treat these files as sensitive whenever they contain real provisioning data.
Binary encoding is not encryption; a binary configuration can also expose
sensitive values when decoded.

## What obfuscation provides

**Settings → Secrets → Obfuscate SNMP secrets** provides limited protection
against casual viewing by masking recognized sensitive SNMP values in the UI.
Protected text uses `********`, and protected hex displays use `FF FF FF FF`.
These fixed placeholders do not reflect the original value's length.

This is display masking, not encryption, password hashing, access control, or
secure deletion. The underlying configuration retains its real values, and
saving or exporting a configuration does not replace those values with masks.
The application must retain real values in memory to edit and compile them.

Masking covers recognized objects and configured custom SNMP rules. It does
not guarantee detection of every credential, vendor-specific object, comment,
or other sensitive field. Review content before taking screenshots, copying
it, or sharing it with others.

The masking checkbox is saved between runs. The shared secret and custom SNMP
masking rules are session-only and are not written to the settings JSON.
Custom rules must be added again after restarting the application.

## Your responsibility

- Restrict file and folder access to authorized users.
- Use storage and transfer protections appropriate for your environment.
- Protect backups, exported binaries, and any other copies of the files.
- Remove real credentials before sharing examples, screenshots, logs, or bug
  reports. Use synthetic values in test configurations.
- Keep sensitive configurations out of source control and public downloads.

Enabling masking does not make a configuration file safe to publish or share.
