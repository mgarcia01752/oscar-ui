---
name: coding-style
description: Apply OSCAR-UI coding conventions when writing, modifying, or reviewing project-owned code, including full public-method documentation banners and multiline conditional branches.
---

# OSCAR-UI coding style

## Class member order

Place constants and fields first, followed by constructors. Group methods by
visibility: public first, then protected, package-private, and private last.
Within each group, keep related operations together in a readable order.
Static methods follow the same visibility ordering as instance methods.
Keep each method's documentation and annotations with its declaration.

Apply this ordering independently to nested classes. Preserve field initializer
order and static initialization behavior when rearranging members. When a class
is modified, arrange its methods consistently without changing their visibility
or behavior merely to satisfy the ordering rule.

## Public function banners

Every explicitly declared public method and public constructor must have a
multiline documentation banner directly above its annotations or declaration.
This includes overrides, accessors, static entry points, and public methods in
package-private classes. Interface methods that are implicitly public follow
the same rule. Do not generate explicit record accessors solely to document
compiler-generated methods; document record components on the record instead.

For Java, use Javadoc containing:

- A clear summary of the operation and its purpose.
- Behavior the caller needs to know: relevant preconditions, side effects,
  state changes, threading requirements, or resource ownership.
- `@param` for every parameter and type parameter, including accepted nulls,
  units, or constraints where applicable.
- `@return` for non-void methods, including meaningful empty/null results.
- `@throws` for declared exceptions and intentional caller-visible failures,
  explaining what triggers them.

Omit inapplicable tags, such as `@return` on constructors or void methods.
Document actual behavior; do not invent guarantees, repeat the method name,
or use a one-line comment or bare `{@inheritDoc}` as the complete banner.

```java
/**
 * Returns the display text for an optional TLV description.
 *
 * <p>Leaves the supplied registry text unchanged. Null and blank descriptions
 * produce an empty string.
 *
 * @param description source description; may be null
 * @return formatted display text, or an empty string when no text is supplied
 */
public String formatDescription(String description) {
    if (description == null) {
        return "";
    }
    return description.strip();
}
```

Use equivalent multiline docstrings for public Python functions and comment
banners for script entry points or reusable shell/PowerShell functions.

## Conditional statements

Java `if`, `else if`, and `else` branches always use braces, with the body on
separate lines. This applies to guard clauses, returns, throws, breaks,
continues, and branches inside lambdas as well as ordinary method bodies.

```java
if (selected == null) {
    return;
} else if (editable) {
    editSelected();
} else {
    showReadOnlyMessage();
}
```

Do not use `if (condition) return;` or `if (condition) { return; }`.
In Python, Bash, and PowerShell, likewise put conditional bodies on separate
lines using the language's normal block syntax. Do not replace branches with
ternaries or chained short-circuit expressions just to evade this rule.

## Descriptive names and domain types

Name variables, parameters, record components, and collections for their domain
meaning, not only their representation. Primitive values need especially clear
names: use `tlvTypeCode`, `valueLengthBytes`, or `sourceEndExclusive` rather than
`type`, `length`, or `end`. Include units and inclusive/exclusive bounds when
they matter. Distinguish byte offsets, Unicode code-point offsets, and Java
UTF-16 character offsets by inspecting the producer and consumer.

- Use `FileType docsisType` in DOCSIS-only code. Use
  `FileType configurationType` when PacketCable and DPoE are also supported;
  do not imply that all supported configurations are DOCSIS.
- Prefer record components such as `tlvPath`, `tlvName`, `encodedValueText`,
  `childTlvs`, and `isContainer` over the generic `path`, `name`, `value`,
  `children`, and `container`. Name source positions for their verified
  meaning, for example `closingBraceOffset` for a container brace position.
- Make collection keys explicit. A catalog map indexed by registry family
  should use `Map<RegistryFamily, Catalog> catalogsByFamily` with an upstream
  domain type if available, otherwise a small validated value type at the UI
  boundary. Do not create a closed enum for an extensible family domain without
  checking how new Oscar catalogs are discovered.
- Use domain types to prevent mixing unrelated identifiers, rather than
  scattering raw `String` or `Integer` keys across APIs. Keep actual text values
  as strings and ordinary counters as integers; do not wrap every primitive.

## Oscar ownership and named constants

Before introducing protocol constants, codec lists, configuration-type IDs,
or registry mappings, search the upstream Oscar source and verify the API in
the UI's pinned dependency. Reuse public upstream definitions when their
semantics match. Do not duplicate an existing upstream definition, access
private fields through reflection, or edit generated dependency checkouts.

Use named constants for protocol values and sentinels, not unexplained numbers
such as `type == 255` or `type == 0`. Ordinary loop origins and arithmetic do
not require meaningless constants. A byte-range limit and an end-marker code
are different concepts even when both happen to equal 255.

Confirmed source locations to check in Oscar (relative to its repository):

- `src/com/comcast/oscar/cablelabsdefinitions/Constants.java` exposes `PAD`,
  `END_OF_FILE`, `CM_MIC`, and `CMTS_MIC`.
- `src/com/comcast/oscar/configurationfile/ConfigurationFileTypeConstants.java`
  defines configuration IDs for DOCSIS, PacketCable, DPoE, and DPoG.
- `src/com/comcast/oscar/dictionary/TlvDefinition.java` represents registry
  metadata; the inspected API exposes the codec and family metadata as strings.
- `src/com/comcast/oscar/dictionary/TlvRegistry.java` contains a private
  `CODECS` validation set. That set includes codecs the UI does not edit,
  including `oid`, `oid_asn1_object6`, and `multi_tlv_byte_array`.

The UI's `EDITABLE_VALUE_CODECS` is an editing-capability allowlist, not an
authoritative list of all Oscar codecs. Do not replace it with every upstream
codec or delete its guard without verifying editing support. Prefer an explicit
name such as `EDITABLE_VALUE_CODECS` and one owner for UI capability decisions.
If Oscar lacks a suitable public capability API, document that gap and propose
an upstream API separately instead of silently widening UI support.

When reviewing duplication, report the upstream location, visibility, meaning,
and whether the pinned artifact exposes it. Distinguish shared protocol facts
from intentionally narrower UI policy. Verify callers and tests before changing
public record components or API types.

## Applying and checking the style

### UI component ownership

Give each substantial dialog or pane one cohesive class. Small labels, buttons,
and standard Qt message/file dialogs belong to their owning component; do not
create classes solely because an element is visible.

- Keep `MainWindow` focused on layout, actions, and coordinating document
  operations. Put tree population, selection mapping, visibility, and context
  menus in `ConfigurationTreePane`; put source display and highlighting in
  `ConfigurationTextPane`.
- Name shared dialogs for their whole role: `TlvEditorDialog` handles both Add
  and Edit using shared validation. Avoid duplicating these implementations.
- Extract custom dialogs with their own controls, such as `SaveFormatDialog`,
  rather than building them inline in window actions.
- Keep component state private. Expose domain operations and Qt signals or
  callbacks, not mutable internal maps. Keep parsing and serialization in the
  service layer. A pane must not start saving files or owning worker threads.
- Preserve Qt parent ownership, dialog disposal, signal behavior, and stable
  object names used by UI tests. Keep widget access on the UI thread and use
  the existing asynchronous workflow for document operations.
- Validate selection, highlighting, nested editing, cancellation, and dialog
  lifetimes with native UI tests after moving responsibilities.

Apply these rules to new and modified code. A request to add this skill does
not itself call for a repository-wide formatting migration. When asked to
review existing code, identify violations within the requested scope; when
asked to migrate it, preserve behavior and run relevant existing checks.

Review complete changed methods, including signatures and comments, rather
than relying on text searches alone. Check that banners match null handling,
exceptions, and side effects, and that expanding branches preserves `else`
binding and execution order. Follow the validation commands in `AGENTS.md`
and the QA skill according to the affected code.
