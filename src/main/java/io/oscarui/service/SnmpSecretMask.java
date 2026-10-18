/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.service;

import com.comcast.oscar.dictionary.TlvDefinition;
import static com.comcast.oscar.cablelabsdefinitions.Constants.SNMPV3_NOTIFICATION_RCVR;
import com.comcast.oscar.netsnmp.NetSNMP;
import io.oscarui.service.OscarDocumentService.Document;
import io.oscarui.service.OscarDocumentService.Node;
import io.oscarui.service.SelectedTlvHexService.HexNode;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Session-only presentation policy; never changes configuration or encoded bytes. */
public final class SnmpSecretMask {
    public static final String TEXT_MASK = "********";
    public static final String HEX_MASK = "FF FF FF FF";
    private static final Pattern NUMERIC_OID = Pattern.compile("[0-2](?:\\.[0-9]+)+");
    private static final Pattern OBJECT_NAME = Pattern.compile("(?:[A-Za-z][A-Za-z0-9-]*::)?[A-Za-z][A-Za-z0-9_-]*(?:\\.[0-9]+)*");
    private static final BigInteger MAX_OID_COMPONENT = new BigInteger("4294967295");
    // Embedded notification receiver alias in Oscar's docsis/dpoe registries.
    private static final List<Integer> EMBEDDED_SECURITY_NAME_PATH = List.of(217, SNMPV3_NOTIFICATION_RCVR, 7);
    private static final String[] SECRET_TLV_NAMES = {
        "SNMPv1v2cCommunityName", "SNMPv3KickstartSecurityName", "SNMPv3NRSecurityName"
    };
    // UI sensitivity rules, not a replacement for Oscar's protocol dictionary.
    // RFC 3414/2786 USM, RFC 3415 VACM and RFC 3584 community-table identities can
    // also occur in OID indexes. Mask the entire matching varbind, including indexes.
    private static final Rule[] BUILTIN_RULES = {
        new Rule("docsDevNmAccessCommunity", "1.3.6.1.2.1.69.1.2.1.4"),
        new Rule("snmpCommunityName", "1.3.6.1.6.3.18.1.1.1.2"),
        new Rule("usmUserAuthKeyChange", "1.3.6.1.6.3.15.1.2.2.1.6"),
        new Rule("usmUserOwnAuthKeyChange", "1.3.6.1.6.3.15.1.2.2.1.7"),
        new Rule("usmUserPrivKeyChange", "1.3.6.1.6.3.15.1.2.2.1.9"),
        new Rule("usmUserOwnPrivKeyChange", "1.3.6.1.6.3.15.1.2.2.1.10"),
        new Rule("usmDHUserAuthKeyChange", "1.3.6.1.3.101.1.1.2.1.1"),
        new Rule("usmDHUserOwnAuthKeyChange", "1.3.6.1.3.101.1.1.2.1.2"),
        new Rule("usmDHUserPrivKeyChange", "1.3.6.1.3.101.1.1.2.1.3"),
        new Rule("usmDHUserOwnPrivKeyChange", "1.3.6.1.3.101.1.1.2.1.4"),
        new Rule("usmDHKickstartSecurityName", "1.3.6.1.3.101.1.2.1.1.4"),
        new Rule("usmUserName", "1.3.6.1.6.3.15.1.2.2.1.2"),
        new Rule("usmUserSecurityName", "1.3.6.1.6.3.15.1.2.2.1.3"),
        new Rule("vacmSecurityName", "1.3.6.1.6.3.16.1.2.1.2"),
        new Rule("usmUserEntry", "1.3.6.1.6.3.15.1.2.2.1"),
        new Rule("vacmSecurityToGroupEntry", "1.3.6.1.6.3.16.1.2.1"),
        new Rule("snmpCommunityEntry", "1.3.6.1.6.3.18.1.1.1")
    };
    private final boolean enabled;
    private final List<Rule> customRules;

    /**
     * One resolved custom SNMP object or subtree to mask, including indexed instances.
     *
     * @param name user-supplied name or numeric OID
     * @param oid canonical numeric OID without a leading dot
     */
    public record Rule(String name, String oid) {}

    /**
     * A replacement in original source coordinates, applied only to a preview.
     *
     * @param sourceStartOffset inclusive UTF-16 source offset
     * @param sourceEndExclusive exclusive UTF-16 source offset
     * @param replacementText fixed-size replacement or safe statement label
     */
    public record Replacement(int sourceStartOffset, int sourceEndExclusive, String replacementText) {}

    /**
     * Creates an immutable session policy from validated custom rules.
     *
     * @param enabled whether to mask displays
     * @param customRules rules returned by resolveRule; copied defensively
     */
    public SnmpSecretMask(boolean enabled, List<Rule> customRules) {
        this.enabled = enabled;
        this.customRules = List.copyOf(customRules);
    }

    /**
     * Returns whether presentation masking is active.
     *
     * @return true when SNMP displays must be obfuscated
     */
    public boolean enabled() {
        return enabled;
    }

    /**
     * Resolves and validates a custom object through Oscar or known standard rules.
     * No wildcard syntax, whitespace, or command arguments are accepted.
     *
     * @param input SNMP name or numeric OID, optionally with a leading dot
     * @return canonical rule suitable for duplicate and component-boundary matching
     * @throws IllegalArgumentException if malformed or unresolved; diagnostics omit input
     */
    public static Rule resolveRule(String input) {
        String name = input.strip();
        String numeric = canonicalOid(name);
        if (numeric == null) {
            if (!OBJECT_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("Enter an SNMP object name or numeric OID.");
            }
            String unqualified = name.substring(name.lastIndexOf(':') + 1);
            for (Rule rule : BUILTIN_RULES) {
                if (matches(unqualified, rule.name())) {
                    numeric = canonicalOid(rule.oid() + unqualified.substring(rule.name().length()));
                    break;
                }
            }
            if (numeric == null) {
                try {
                    numeric = canonicalOid(NetSNMP.toDottedOID(name));
                } catch (RuntimeException exception) {
                    throw new IllegalArgumentException("OSCAR could not resolve that name. Enter its numeric OID.");
                }
            }
        }
        if (numeric == null) {
            throw new IllegalArgumentException("OSCAR could not resolve that name. Enter its numeric OID.");
        }
        return new Rule(name, numeric);
    }

    /**
     * Checks direct SNMP credential TLVs using registry names and scoped aliases.
     *
     * @param definition Oscar registry definition; null is accepted
     * @return true for a protected scalar when masking is enabled
     */
    public boolean protects(TlvDefinition definition) {
        return definition != null && enabled && directSecret(definition.name(), definition.path());
    }

    /**
     * Checks whether this row contains a protected SNMP value or identity index.
     *
     * @param node parsed source or binary-projected node
     * @return true only for protected scalar rows with masking active
     */
    public boolean protects(Node node) {
        return enabled && (directSecret(node.tlvName(), node.tlvPath()) || snmpLabel(node) != null);
    }

    /**
     * Checks a complete subtree, including secrets hidden within parent bytes.
     *
     * @param node root of the subtree
     * @return true when any scalar descendant requires masking
     */
    public boolean protectsSubtree(Node node) {
        return protects(node) || node.childTlvs().stream().anyMatch(this::protectsSubtree);
    }

    /**
     * Formats a tree value without exposing secret lengths or indexed identities.
     *
     * @param node document row
     * @return original value or fixed mask with a safe standard object label
     */
    public String value(Node node) {
        if (!protects(node)) {
            return node.encodedValueText();
        }
        String label = snmpLabel(node);
        return label == null ? TEXT_MASK : label + " " + TEXT_MASK;
    }

    /**
     * Builds source replacements while leaving the document untouched.
     * Matching statements are replaced as a whole so OID indexes and internal
     * comments cannot leak credentials. Other comments containing known secret
     * literals are masked as a whole, without changing unrelated source.
     *
     * @param document source snapshot
     * @return ordered, non-overlapping replacements for the preview
     */
    public List<Replacement> replacements(Document document) {
        var replacements = new ArrayList<Replacement>();
        collect(document.root(), replacements);
        if (enabled) {
            maskComments(document, replacements);
        }
        replacements.sort(java.util.Comparator.comparingInt(Replacement::sourceStartOffset));
        return List.copyOf(replacements);
    }

    /**
     * Masks encoded rows and every ancestor that includes protected child bytes.
     *
     * @param source selected source node corresponding to the encoded tree
     * @param encoded Oscar's encoded row tree with matching child order
     * @return safe display-only tree; original bytes remain unchanged
     */
    public HexNode hex(Node source, HexNode encoded) {
        var children = new ArrayList<HexNode>();
        for (int index = 0; index < encoded.children().size(); index++) {
            children.add(hex(source.childTlvs().get(index), encoded.children().get(index)));
        }
        return new HexNode(encoded.name(), encoded.description(),
                protectsSubtree(source) ? HEX_MASK : encoded.hex(), children);
    }

    private void maskComments(Document document, List<Replacement> replacements) {
        var literals = new ArrayList<String>();
        for (Replacement replacement : replacements) {
            String statement = document.text().substring(replacement.sourceStartOffset(), replacement.sourceEndExclusive());
            var quotedValue = Pattern.compile("\"([^\"]+)\"").matcher(statement);
            while (quotedValue.find()) {
                literals.add(quotedValue.group(1));
            }
            // Direct scalar strings may be unquoted in Oscar syntax.
            String tail = statement.replaceFirst("^\\S+\\s+", "").strip();
            if (tail.endsWith(";")) {
                literals.add(tail.substring(0, tail.length() - 1).strip());
            }
        }
        String source = document.text();
        boolean quoted = false;
        for (int offset = 0; offset < source.length(); offset++) {
            if (quoted && source.charAt(offset) == '\\') {
                offset++;
                continue;
            }
            if (source.charAt(offset) == '"') {
                quoted = !quoted;
            }
            if (!quoted && source.startsWith("/*", offset)) {
                int commentEndOffset = source.indexOf("*/", offset + 2);
                if (commentEndOffset < 0) {
                    break;
                }
                int commentStartOffset = offset;
                String comment = source.substring(offset, commentEndOffset + 2);
                if (literals.stream().anyMatch(literal -> !literal.isBlank() && comment.contains(literal))
                        && replacements.stream().noneMatch(replacement -> commentStartOffset >= replacement.sourceStartOffset() && commentStartOffset < replacement.sourceEndExclusive())) {
                    replacements.add(new Replacement(offset, commentEndOffset + 2, "/* " + TEXT_MASK + " */"));
                }
                offset = commentEndOffset + 1;
            }
        }
    }

    private void collect(Node node, List<Replacement> replacements) {
        if (protects(node)) {
            replacements.add(new Replacement(node.sourceStartOffset(), node.sourceEndExclusive(),
                    node.tlvName() + " " + value(node) + ";"));
        } else {
            node.childTlvs().forEach(child -> collect(child, replacements));
        }
    }

    private String snmpLabel(Node node) {
        if (!node.tlvName().equals("Snmp11") && !node.tlvName().equals("Snmp64")) {
            return null;
        }
        String object = node.encodedValueText().strip().split("\\s+", 2)[0];
        String numeric = canonicalOid(object);
        String unqualified = object.substring(object.lastIndexOf(':') + 1);
        for (Rule rule : BUILTIN_RULES) {
            if ((numeric != null && matches(numeric, rule.oid())) || matches(unqualified, rule.name())) {
                return rule.name();
            }
        }
        for (Rule rule : customRules) {
            if ((numeric != null && matches(numeric, rule.oid())) || matches(unqualified, rule.name())) {
                return "CustomSnmpSecret";
            }
        }
        // Decoded documents use numeric OIDs. An unresolved source-only SNMP
        // draft cannot safely be classified, so hide its entire varbind.
        if (numeric == null) {
            try {
                numeric = resolveRule(object).oid();
            } catch (IllegalArgumentException exception) {
                return "SnmpSecret";
            }
            for (Rule rule : customRules) {
                if (matches(numeric, rule.oid())) {
                    return "CustomSnmpSecret";
                }
            }
            for (Rule rule : BUILTIN_RULES) {
                if (matches(numeric, rule.oid())) {
                    return rule.name();
                }
            }
        }
        return null;
    }

    private static boolean directSecret(String name, List<Integer> path) {
        return java.util.Arrays.asList(SECRET_TLV_NAMES).contains(name)
                || (name.equals("SecurityName") && path.equals(EMBEDDED_SECURITY_NAME_PATH));
    }

    private static boolean matches(String candidate, String prefix) {
        return candidate.equals(prefix) || candidate.startsWith(prefix + ".");
    }

    private static String canonicalOid(String supplied) {
        if (supplied == null) {
            return null;
        }
        String numeric = supplied.startsWith(".") ? supplied.substring(1) : supplied;
        if (!NUMERIC_OID.matcher(numeric).matches()) {
            return null;
        }
        var components = numeric.split("\\.");
        var canonical = new ArrayList<String>();
        for (String component : components) {
            var number = new BigInteger(component);
            if (number.compareTo(MAX_OID_COMPONENT) > 0) {
                return null;
            }
            canonical.add(number.toString());
        }
        if (!canonical.getFirst().equals("2") && new BigInteger(canonical.get(1)).compareTo(BigInteger.valueOf(39)) > 0) {
            return null;
        }
        return String.join(".", canonical);
    }
}
