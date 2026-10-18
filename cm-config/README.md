# Cable modem configuration examples

Sample configurations for opening, editing, and inspecting in OSCAR-UI.

| Example | Contents |
| --- | --- |
| [1 Gbps downstream / 100 Mbps upstream](examples/docsis/1gbps-ds-100mbps-us.txt) | Network access enabled, upstream/downstream service flows, 6 MB (6,000,000 bytes) downstream maximum traffic burst, read/write SNMP communities, BPI enabled, and synthetic USM/VACM masking records |

## Use an example

Download the linked text file, then choose **File → Open** in OSCAR-UI.
See the [file operations guide](../docs/ui/files.md) for editing and saving as
text or binary.

These examples use synthetic values, not production credentials. The SNMP
access rows in the example allow any IPv4 manager through the cable interface;
adapt them before deployment. Its USM/VACM records exercise masking and do not
configure a complete SNMPv3 user or access policy.

Read [configuration file security concerns](../docs/configuration-file-security.md)
before saving configurations containing sensitive information.
