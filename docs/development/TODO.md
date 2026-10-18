# Development TODO

[Development guide](README.md) · [Architecture and checklists](ui-architecture.md)

## VACM and SNMPv3 symbolic configuration

Status: deferred at the user's request. Keep the working numeric OIDs in
`cm-config/examples/docsis/1gbps-ds-100mbps-us.txt` until this is resolved.

OSCAR 3.1.2 adds base-object mappings for USM, VACM, DH key-change, and community
objects. Its LUT lookup still requires an exact match. In the OSCAR-UI runtime,
the example's indexed symbolic names fail compilation with “Unknown object
identifier”: `usmUserSecurityName`, `usmUserAuthKeyChange`,
`usmUserPrivKeyChange`, and `vacmGroupName`, each followed by its numeric index.
The bundled upstream MIBs resolve these instances when Net-SNMP is explicitly
pointed at them; the LUT update alone does not provide portable instance lookup.

`vacmGroupName` has base OID `1.3.6.1.6.3.16.1.2.1.3`. Its example row suffix
is `.3.8.108.97.98.45.117.115.101.114`: security model 3 (USM), followed by the
length and ASCII bytes of `lab-user`. The assigned group is `lab-group`.
A symbolic column name does not remove the required row index.

- [ ] Fix indexed name/OID resolution in upstream OSCAR, preserving numeric
  instance suffixes and component boundaries. Do not add a duplicate UI LUT.
- [ ] Test both translation directions without an external Net-SNMP install,
  including optional leading dots and existing nonempty runtime caches.
- [ ] Publish the upstream fix and update OSCAR-UI's release/checksum pins.
- [ ] Replace the four example numeric object prefixes with symbolic names;
  preserve instance indexes, TLV order, and encoded bytes.
- [ ] Verify text compilation, binary tree projection, and fixed-length masking
  of values and identity-bearing indexes in text, tree, and hex views.
- [ ] Revisit a complete USM user and VACM access/view configuration separately.
  Current records are synthetic masking fixtures, not usable credentials or a
  complete access policy. A VACM group mapping alone grants no access.
