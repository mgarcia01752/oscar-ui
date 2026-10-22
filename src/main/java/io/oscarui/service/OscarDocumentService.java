/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.service;

import com.comcast.oscar.configurationfile.ConfigurationFileExport;
import com.comcast.oscar.configurationfile.TemplateBannerLevel;
import static com.comcast.oscar.cablelabsdefinitions.Constants.CM_MIC;
import static com.comcast.oscar.cablelabsdefinitions.Constants.CMTS_MIC;
import static com.comcast.oscar.cablelabsdefinitions.Constants.L2VPN_MAC_AGING_ENC;
import com.comcast.oscar.dictionary.TlvDefinition;
import com.comcast.oscar.dictionary.TlvDictionary;
import com.comcast.oscar.dictionary.TlvRegistry;
import com.comcast.oscar.parser.TlvConfigurationFileParser;
import com.comcast.oscar.parser.tlvLexer;
import com.comcast.oscar.parser.tlvParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigInteger;
import java.net.JarURLConnection;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.antlr.v4.runtime.*;

public final class OscarDocumentService {
    private final CommonTlvPreferences commonTlvs = new CommonTlvPreferences();
    private static final String REFERENCE_MARKER = "/* OSCAR-UI reference template: not deployment defaults. */";

    private static final String LEGACY_REFERENCE_MARKER = "/* Oscar Desktop reference template: not deployment defaults. */";

    // UI editing support is intentionally narrower than Oscar registry support.
    private static final Set<String> EDITABLE_VALUE_CODECS = Set.of("integer", "unsigned_integer", "byte", "string",
            "string_null_terminated", "string_bits", "byte_array", "double_byte_array", "mac_address",
            "byte_array_ipv4_address", "byte_array_ipv6_address", "transport_address_ipv4_address",
            "transport_address_ipv6_address", "byte_array_inet_transport_address", "byte_array_inet_address");

    /**
     * Identifies a configuration grammar header and its matching Oscar catalog.
     *
     * @param id Oscar configuration-type identifier
     * @param header configuration grammar header shown to the user
     * @param family extensible registry-family name supplied by Oscar
     * @param edition registry edition metadata
     */
    public record FileType(int id, String header, String family, String edition) {
        /**
         * Returns the configuration header used in file-type controls.
         *
         * @return Oscar grammar header for this configuration type
         */
        @Override public String toString() { return header; }
    }

    /**
     * Immutable parsed TLV instance, distinct from its upstream registry definition.
     *
     * @param tlvPath hierarchical TLV identifiers; empty for the root
     * @param tlvName registry name or unmapped reference name
     * @param encodedValueText scalar text in Oscar syntax, or empty for a container
     * @param childTlvs child instances in document order
     * @param sourceStartOffset inclusive UTF-16 offset in the source string
     * @param sourceEndExclusive exclusive UTF-16 offset in the source string
     * @param terminatorOffset UTF-16 offset of the closing brace or semicolon
     * @param isContainer whether registry metadata identifies a container
     */
    public record Node(List<Integer> tlvPath, String tlvName, String encodedValueText, List<Node> childTlvs,
                       int sourceStartOffset, int sourceEndExclusive, int terminatorOffset, boolean isContainer) {
        /** UI-only path marker for a reference entry without an Oscar definition. */
        public static final int UNMAPPED_TLV_TYPE_CODE = -1;

        /**
         * Creates a TLV snapshot with immutable path and child lists.
         *
         * <p>The supplied lists are copied; source offsets refer to the original Java string.
         *
         * @param tlvPath non-null hierarchical TLV identifiers; empty for the document root
         * @param tlvName registry name or original unmapped reference name
         * @param encodedValueText scalar text in Oscar syntax, or empty for a container
         * @param childTlvs non-null child snapshots, copied into an immutable list
         * @param sourceStartOffset inclusive UTF-16 source offset
         * @param sourceEndExclusive exclusive UTF-16 source offset
         * @param terminatorOffset UTF-16 offset of the closing brace or statement terminator
         * @param isContainer whether registry metadata identifies a container
         */
        public Node { tlvPath = List.copyOf(tlvPath); childTlvs = List.copyOf(childTlvs); }
    }

    /**
     * Original binary snapshot used for conflict checks and source protection.
     *
     * @param path normalized source binary path
     * @param diskHash SHA-256 fingerprint of the source binary
     * @param bytes original bytes, defensively copied on construction and access
     */
    public record BinaryOrigin(Path path, String diskHash, byte[] bytes) {
        /**
         * Captures the source binary and its on-disk identity.
         *
         * <p>Copies the supplied bytes so later caller mutations cannot change the snapshot.
         *
         * @param path normalized binary source path
         * @param diskHash SHA-256 disk fingerprint, or null for an unsaved document
         * @param bytes non-null original binary bytes to copy
         */
        public BinaryOrigin { bytes = bytes.clone(); }
        /**
         * Returns an independent copy of the captured binary.
         *
         * <p>The returned buffer can be modified without changing this origin.
         *
         * @return defensive copy of the original binary bytes
         */
        @Override public byte[] bytes() { return bytes.clone(); }
    }

    /**
     * Immutable editing snapshot with separate text and binary source identities.
     *
     * @param path text source path, or null for unsaved or binary-origin documents
     * @param text complete editable configuration source
     * @param configurationType resolved Oscar configuration type
     * @param root parsed root containing all TLV instances
     * @param diskHash text source fingerprint, or null without a text source
     * @param reference whether reference-template placeholders are permitted
     * @param origin binary source identity, or null for text-only documents
     */
    public record Document(Path path, String text, FileType configurationType, Node root, String diskHash,
                           boolean reference, BinaryOrigin origin) {
        /**
         * Creates a document snapshot without a binary origin.
         *
         * <p>The document may represent an unsaved draft with no path or disk hash.
         *
         * @param path source path; null is permitted for an unsaved text document
         * @param text configuration source text
         * @param configurationType non-null supported Oscar configuration type
         * @param root parsed document root
         * @param diskHash SHA-256 disk fingerprint, or null for an unsaved document
         * @param reference whether this snapshot contains reference-template placeholders
         */
        public Document(Path path, String text, FileType configurationType, Node root, String diskHash, boolean reference) {
            this(path, text, configurationType, root, diskHash, reference, null);
        }
        /**
         * Returns a new snapshot associated with a binary origin.
         *
         * <p>Preserves source text, parsed nodes, and reference-template state.
         *
         * @param origin binary origin to associate, or null to clear the association
         * @return document snapshot carrying the supplied origin
         */
        public Document withOrigin(BinaryOrigin origin) {
            return new Document(path, text, configurationType, root, diskHash, reference, origin);
        }
    }

    private record Catalog(String family, String edition, TlvRegistry registry) {}

    /** Extensible registry-family identifier; Oscar currently exposes this as text. */
    private record RegistryFamily(String registryName) {
        RegistryFamily {
            if (registryName == null || registryName.isBlank()) {
                throw new IllegalArgumentException("Registry family must not be blank");
            }
        }
    }

    // Current UI editing scope; these bounds are not protocol capability limits.
    private Map<RegistryFamily, Catalog> catalogsByFamily;

    private List<FileType> types;

    /**
     * Returns this editor service's shared Common TLV presentation preferences.
     * @return per-user preference owner, initially minimum-only and session-scoped
     */
    public CommonTlvPreferences commonTlvs() {
        return commonTlvs;
    }

    /**
     * Discovers supported Oscar configuration types and caches the catalogs.
     *
     * <p>Loads bundled registries and matches them to Oscar grammar headers.
     *
     * @return immutable supported types ordered by header
     * @throws InspectionException if catalogs or supported document types cannot be discovered
     */
    public synchronized List<FileType> types() throws InspectionException {
        if (types != null) {
            return types;
        }
        try {
            catalogsByFamily = discoverCatalogs();
            var parser = parser("");
            var supported = new ArrayList<FileType>();
            var tokens = parser.getATN().nextTokens(parser.getATN().ruleToStartState[tlvParser.RULE_configType]);
            for (int token : tokens.toList()) {
                if (token <= 0) {
                    continue;
                }
                String literal = parser.getTokenNames()[token];
                if (!literal.startsWith("'")) {
                    continue;
                }
                String header = literal.substring(1, literal.length() - 1);
                var context = parser(header).configType();
                var listener = new TlvConfigurationFileParser();
                listener.enterConfigType(context);
                int configurationTypeId = listener.getConfigurationFileType();
                if (configurationTypeId < 0) {
                    continue;
                }
                var definitions = new TlvDictionary().getAllTlvDefinition(configurationTypeId);
                for (Catalog catalog : catalogsByFamily.values()) {
                    var roots = catalog.registry().children(List.of());
                    if (roots.size() != definitions.length()) {
                        continue;
                    }
                    boolean matches = true;
                    for (int index = 0; index < definitions.length(); index++) {
                        var definition = definitions.getJSONObject(index);
                        var entry = catalog.registry().find(List.of(definition.getInt("Type")));
                        if (entry == null || !entry.name().equals(definition.getString("TlvName"))) {
                            matches = false;
                            break;
                        }
                    }
                    if (matches) {
                        supported.add(new FileType(configurationTypeId, header, catalog.family(), catalog.edition()));
                        break;
                    }
                }
            }
            if (supported.isEmpty()) {
                throw new IOException("No supported document types");
            }
            types = supported.stream().sorted(Comparator.comparing(FileType::header)).toList();
            return types;
        } catch (IOException | RuntimeException error) {
            throw new InspectionException("Cannot discover Oscar document types and bundled catalogs.", error);
        }
    }

    /**
     * Reads a bounded UTF-8 configuration and parses its TLV structure.
     *
     * <p>Leaves the source file unchanged; this operation does not verify MICs.
     *
     * @param path source or destination filesystem path; must not be null
     * @return parsed document with normalized source path and disk hash
     * @throws InspectionException if the file cannot be read, parsed, or imported without unsupported data
     */
    public Document open(Path path) throws InspectionException {
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(OscarInspectionService.MAX_BYTES + 1);
            if (bytes.length == 0 || bytes.length > OscarInspectionService.MAX_BYTES) {
                throw new InspectionException("Select a nonempty configuration text file up to 1 MiB.");
            }
            return openText(path, bytes);
        } catch (IOException error) {
            throw new InspectionException("Cannot read UTF-8 text. Check the file and permissions.", error);
        }
    }

    /**
     * Creates an empty configuration or an Oscar reference template.
     *
     * <p>Reference templates are examples, not deployment defaults.
     *
     * @param configurationType non-null supported Oscar configuration type
     * @param allTlvs true for a full reference template, false for an empty draft
     * @return unsaved parsed document of the requested configuration type
     * @throws InspectionException if the configuration type or generated template is unsupported
     */
    public Document create(FileType configurationType, boolean allTlvs) throws InspectionException {
        return create(configurationType, allTlvs, TemplateBannerLevel.SPDX_AUTHOR);
    }

    /**
     * Creates a draft with the requested OSCAR template banner.
     *
     * @param configurationType supported file type
     * @param allTlvs whether to include reference TLVs
     * @param bannerLevel non-null banner selection, used for reference templates
     * @return unsaved parsed document
     * @throws InspectionException if template generation fails
     */
    public Document create(FileType configurationType, boolean allTlvs, TemplateBannerLevel bannerLevel)
            throws InspectionException {
        Objects.requireNonNull(bannerLevel);
        if (!types().contains(configurationType)) {
            throw new InspectionException("Unsupported configuration type.");
        }
        try {
            String text = configurationType.header() + " {\n}\n";
            if (allTlvs) {
                var exporter = new ConfigurationFileExport(configurationType.id());
                exporter.setTemplateBannerLevel(bannerLevel);
                String exported = exporter.toPrettyPrint(ConfigurationFileExport.EXPORT_DEFAULT_TLV);
                Token header = parser(exported).getTokenStream().LT(1);
                text = REFERENCE_MARKER + "\n" + exported.substring(0, header.getStartIndex()) + configurationType.header()
                        + exported.substring(header.getStopIndex() + 1);
            }
            return parse(null, text, null, allTlvs);
        } catch (RuntimeException error) {
            throw new InspectionException("Oscar could not generate this configuration template.", error);
        }
    }

    /**
     * Lists TLVs that may be inserted into the selected container.
     *
     * <p>Applies UI editing restrictions, generated-field exclusions, and instance limits.
     *
     * @param document non-null document snapshot to inspect or modify
     * @param parent non-null insertion parent from the supplied document
     * @return available definitions, or an empty list for an invalid parent
     * @throws InspectionException if the bundled catalogs cannot be initialized
     */
    public List<TlvDefinition> choices(Document document, Node parent) throws InspectionException {
        return addDefinitions(document, parent).stream()
                .filter(definition -> insertionIssue(document, parent, definition).isEmpty()).toList();
    }

    /**
     * Lists every registry definition belonging to an insertion parent, including unavailable rows.
     *
     * @param document current document snapshot
     * @param parent insertion container from that snapshot
     * @return full parent catalog, or an empty list for an invalid parent
     * @throws InspectionException if the catalogs cannot be initialized
     */
    public List<TlvDefinition> addDefinitions(Document document, Node parent) throws InspectionException {
        types();
        if (!contains(document.root(), parent) || !parent.isContainer()
                || parent.tlvPath().contains(Node.UNMAPPED_TLV_TYPE_CODE)) {
            return List.of();
        }
        return catalogsByFamily.get(new RegistryFamily(document.configurationType().family()))
                .registry().children(parent.tlvPath());
    }

    /**
     * Explains why a catalog definition cannot currently be inserted.
     *
     * @param document current document snapshot
     * @param parent insertion container
     * @param definition definition from the parent's catalog
     * @return empty when available, otherwise a user-facing restriction
     */
    public String insertionIssue(Document document, Node parent, TlvDefinition definition) {
        if (isGeneratedMic(document.configurationType(), definition.path())) {
            return "Generated automatically";
        }
        String issue = editingIssue(document.configurationType(), definition);
        if (!issue.isEmpty()) {
            return "Editing not supported: " + issue;
        }
        if (definition.maxInstances() == 0) {
            return "Not permitted in this context";
        }
        if (definition.maxInstances() > 0 && parent.childTlvs().stream()
                .filter(child -> child.tlvPath().equals(definition.path())).count() >= definition.maxInstances()) {
            return "Instance limit reached — edit existing entry in tree";
        }
        return "";
    }


    /**
     * Identifies generated CM and CMTS integrity fields for a configuration family.
     *
     * @param configurationType non-null supported Oscar configuration type
     * @param path non-null hierarchical TLV identifiers
     * @return true for a generated top-level MIC path in a supported family
     */
    public boolean isGeneratedMic(FileType configurationType, List<Integer> path) {
        return Set.of("docsis", "docsis40", "dpoe").contains(configurationType.family())
                && (path.equals(List.of(CM_MIC)) || path.equals(List.of(CMTS_MIC)));
    }

    /**
     * Determines whether the registry defines children below a TLV.
     *
     * <p>Load the catalogs through types(), open(), or create() before calling.
     *
     * @param configurationType non-null supported Oscar configuration type
     * @param definition non-null registry definition for the configuration type
     * @return true when the registry contains child definitions
     */
    public boolean isContainer(FileType configurationType, TlvDefinition definition) {
        return !catalogsByFamily.get(new RegistryFamily(configurationType.family())).registry().children(definition.path()).isEmpty();
    }

    /**
     * Explains why the UI cannot edit a registry definition.
     *
     * <p>Checks catalog identity and UI scope; this is not complete value validation.
     *
     * @param configurationType non-null supported Oscar configuration type
     * @param definition non-null registry definition for the configuration type
     * @return restriction message, or an empty string for a supported definition
     */
    public String editingIssue(FileType configurationType, TlvDefinition definition) {
        var registry = catalogsByFamily.get(new RegistryFamily(configurationType.family())).registry();
        if (!definition.equals(registry.find(definition.path()))) {
            return "Unmapped catalog entry";
        }
        if (configurationType.family().equals("docsis40")) {
            int rootTlvTypeCode = definition.path().get(0);
            if (rootTlvTypeCode == L2VPN_MAC_AGING_ENC) {
                return "Deferred editing scope";
            }
            for (int size = 1; size <= definition.path().size(); size++) {
                var ancestor = registry.find(definition.path().subList(0, size));
                String name = ancestor == null ? "" : ancestor.name().toLowerCase(Locale.ROOT);
                if (name.contains("l2vpn")) {
                    return "Deferred editing scope";
                }
            }
        }
        if (definition.name().chars().anyMatch(Character::isWhitespace)) {
            return "Unsupported text name";
        }
        return isContainer(configurationType, definition) || isSnmpValue(definition)
                || CertificateInput.supports(definition) || EDITABLE_VALUE_CODECS.contains(definition.codec()) ? "" : "Unsupported encoding";
    }

    /**
     * Checks a proposed value against the supported Oscar text syntax.
     *
     * <p>Checks integer width where applicable; does not encode or modify the document.
     *
     * @param configurationType non-null supported Oscar configuration type
     * @param definition non-null registry definition for the configuration type
     * @param value non-null proposed scalar text in Oscar syntax
     * @return validation message, or an empty string when the value passes these checks
     */
    public String valueIssue(FileType configurationType, TlvDefinition definition, String value) {
        String issue = editingIssue(configurationType, definition);
        if (!issue.isEmpty()) {
            return issue;
        }
        if (isContainer(configurationType, definition)) {
            return "";
        }
        String supplied = value.strip();
        if (supplied.isEmpty()) {
            return "Enter a value";
        }
        if (supplied.indexOf('\n') >= 0 || supplied.indexOf('\r') >= 0) {
            return "Enter a single TLV value";
        }
        if (CertificateInput.supports(definition)) {
            try {
                CertificateInput.parse(supplied);
                OscarTextCompileCheck.validate(configurationType.header() + " { "
                        + definition.name() + " " + supplied + "; }");
                return "";
            } catch (InspectionException | IllegalArgumentException error) {
                return "Enter valid certificate HEX bytes";
            }
        }
        if (isSnmpValue(definition)) {
            try {
                String statement = definition.name() + " " + supplied + ";";
                var registry = catalogsByFamily.get(new RegistryFamily(configurationType.family())).registry();
                for (int depth = definition.path().size() - 1; depth > 0; depth--) {
                    var ancestor = registry.find(definition.path().subList(0, depth));
                    statement = ancestor.name() + " { " + statement + " }";
                }
                String source = configurationType.header() + " { " + statement + " }";
                var probe = parseDecodedTree(source, configurationType);
                Node node = probe.root();
                for (int depth = 1; depth <= definition.path().size(); depth++) {
                    if (node.childTlvs().size() != 1) {
                        return "Enter one SNMP OID, type, and value";
                    }
                    node = node.childTlvs().getFirst();
                    if (!node.tlvPath().equals(definition.path().subList(0, depth))) {
                        return "Enter one SNMP OID, type, and value";
                    }
                }
                OscarTextCompileCheck.validate(source);
                return "";
            } catch (InspectionException | RuntimeException error) {
                return "Enter a valid SNMP OID, type, and quoted value";
            }
        }
        if (Set.of("integer", "unsigned_integer", "byte").contains(definition.codec())) {
            try {
                var number = new BigInteger(supplied);
                if (definition.codec().equals("unsigned_integer") && (number.signum() < 0
                        || number.bitLength() > definition.lengthMax() * 8)) {
                    return "Value exceeds unsigned codec width";
                }
            } catch (NumberFormatException error) { return "Enter an integer value"; }
        }
        try {
            var probe = parser(definition.name() + " " + supplied + ";");
            var parsed = probe.startTLV();
            if (probe.getTokenStream().LA(1) != Token.EOF || parsed.tlv() == null) {
                return "Enter a single scalar value";
            }
        } catch (RuntimeException error) { return "Value does not match Oscar text syntax"; }
        return "";
    }

    /**
     * Inserts one TLV and scaffolds its immediate children when needed.
     *
     * <p>Reparses the result and verifies that the insertion adds only expected nodes.
     *
     * @param document non-null document snapshot to inspect or modify
     * @param parent non-null insertion parent from the supplied document
     * @param definition non-null registry definition for the configuration type
     * @param value non-null proposed scalar text in Oscar syntax
     * @return new document snapshot preserving the original binary association
     * @throws InspectionException if insertion is disallowed or the proposed source cannot be parsed safely
     */
    public Document insert(Document document, Node parent, TlvDefinition definition, String value)
            throws InspectionException {
        if (!contains(document.root(), parent) || !choices(document, parent).contains(definition)) {
            throw new InspectionException("This TLV is not available in the selected container.");
        }
        boolean container = isContainer(document.configurationType(), definition);
        String supplied = value.strip();
        String issue = valueIssue(document.configurationType(), definition, value);
        if (!issue.isEmpty()) {
            throw new InspectionException(issue + ".");
        }
        int depth = parent.tlvPath().size() + 1;
        String newline = document.text().contains("\r\n") ? "\r\n" : "\n";
        var children = container ? scaffoldChildren(document.configurationType(), definition.path()) : List.<TlvDefinition>of();
        String entry = container ? definition.name() + scaffoldBody(children, depth, newline)
                : definition.name() + " " + supplied + ";";
        var probe = parser(entry);
        try {
            var parsed = probe.startTLV();
            if (probe.getTokenStream().LA(1) != Token.EOF
                    || !parsed.getStart().getText().equals(definition.name()) || !container && parsed.sub() != null) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException error) {
            throw new InspectionException("The value does not match Oscar's text syntax.", error);
        }
        String addition = newline + "    ".repeat(depth) + entry + newline + "    ".repeat(depth - 1);
        String text;
        if (!parent.tlvPath().isEmpty() && document.text().charAt(parent.terminatorOffset()) == ';') {
            text = document.text().substring(0, parent.terminatorOffset()) + " {" + addition + "}"
                    + document.text().substring(parent.sourceEndExclusive());
        } else {
            text = document.text().substring(0, parent.terminatorOffset()) + addition + document.text().substring(parent.terminatorOffset());
        }
        Document changed = parse(document.path(), text, document.diskHash(), document.reference());
        if (count(changed.root()) != count(document.root()) + 1 + children.size()) {
            throw new InspectionException("A value must not insert additional TLV statements.");
        }
        return changed.withOrigin(document.origin());
    }

    /**
     * Determines whether an empty container can receive registry placeholders.
     *
     * @param document non-null document snapshot to inspect or modify
     * @param node selected TLV snapshot; may be null for capability checks
     * @return true for an owned, supported container with available child definitions
     */
    public boolean canPopulate(Document document, Node node) {
        return node != null && !node.tlvPath().isEmpty() && node.isContainer() && node.childTlvs().isEmpty()
                && contains(document.root(), node) && definition(document, node) != null
                && editingIssue(document.configurationType(), definition(document, node)).isEmpty()
                && !scaffoldChildren(document.configurationType(), node.tlvPath()).isEmpty();
    }

    /**
     * Adds immediate registry placeholders to an empty TLV container.
     *
     * <p>Preserves existing entries and does not recursively populate grandchildren.
     *
     * @param document non-null document snapshot to inspect or modify
     * @param container non-null empty container belonging to the document
     * @return reparsed document containing the immediate child placeholders
     * @throws InspectionException if the container cannot be populated or the resulting structure is invalid
     */
    public Document populate(Document document, Node container) throws InspectionException {
        if (!canPopulate(document, container)) {
            throw new InspectionException("Select an empty supported TLV container.");
        }
        var children = scaffoldChildren(document.configurationType(), container.tlvPath());
        String newline = document.text().contains("\r\n") ? "\r\n" : "\n";
        String text = document.text().substring(0, container.terminatorOffset())
                + scaffoldBody(children, container.tlvPath().size(), newline)
                + document.text().substring(container.sourceEndExclusive());
        var changed = parse(document.path(), text, document.diskHash(), document.reference());
        if (count(changed.root()) != count(document.root()) + children.size()) {
            throw new InspectionException("Container population must preserve existing TLV entries.");
        }
        return changed.withOrigin(document.origin());
    }

    /**
     * Looks up the catalog definition corresponding to a document node.
     *
     * <p>The catalog must already be loaded for the document configuration type.
     *
     * @param document non-null document snapshot to inspect or modify
     * @param node non-null TLV snapshot
     * @return matching definition, or null for an unmapped reference entry
     */
    public TlvDefinition definition(Document document, Node node) {
        return catalogsByFamily.get(new RegistryFamily(document.configurationType().family())).registry().find(node.tlvPath());
    }

    /**
     * Checks whether a TLV and all descendants may be deleted.
     *
     * <p>Protects the document root and generated MIC entries.
     *
     * @param document non-null document snapshot to inspect or modify
     * @param node selected TLV snapshot; may be null for capability checks
     * @return true when the entire selected subtree is deletable
     */
    public boolean canDelete(Document document, Node node) {
        if (node == null || node.tlvPath().isEmpty() || !contains(document.root(), node)
                || isGeneratedMic(document.configurationType(), node.tlvPath())) {
            return false;
        }
        var definition = definition(document, node);
        return definition != null && (editingIssue(document.configurationType(), definition).isEmpty()
            || node.encodedValueText().isEmpty() && node.childTlvs().isEmpty()
                && Set.of("Unsupported encoding", "Unsupported text name").contains(editingIssue(document.configurationType(), definition)))
                && node.childTlvs().stream().allMatch(child -> canDelete(document, child));
    }

    /**
     * Checks whether the selected node supports scalar-value editing.
     *
     * <p>Container population and deletion use separate operations.
     *
     * @param document non-null document snapshot to inspect or modify
     * @param node selected TLV snapshot; may be null for capability checks
     * @return true for a deletable supported scalar with no children
     */
    public boolean canEdit(Document document, Node node) {
        return canDelete(document, node) && !node.isContainer() && node.childTlvs().isEmpty()
            && editingIssue(document.configurationType(), definition(document, node)).isEmpty();
    }

    /**
     * Replaces the selected scalar token or complete SNMP value in configuration text.
     *
     * <p>Preserves surrounding comments and whitespace and verifies the node count.
     *
     * @param document non-null document snapshot to inspect or modify
     * @param node selected TLV snapshot; may be null for capability checks
     * @param value non-null proposed scalar text in Oscar syntax
     * @return reparsed document, or the original snapshot when the value is unchanged
     * @throws InspectionException if editing is disallowed or the replacement value is invalid
     */
    public Document replaceValue(Document document, Node node, String value) throws InspectionException {
        if (!canEdit(document, node)) {
            throw new InspectionException("This TLV cannot be edited.");
        }
        String supplied = value.strip();
        if (supplied.equals(node.encodedValueText())) {
            return document;
        }
        String issue = valueIssue(document.configurationType(), definition(document, node), supplied);
        if (!issue.isEmpty()) {
            throw new InspectionException(issue + ".");
        }
        // Ordinary scalar values occupy one token; SNMP values span the OID,
        // type, and quoted value up to the statement terminator.
        var tokens = parser(document.text().substring(node.sourceStartOffset(), node.sourceEndExclusive())).getTokenStream();
        Token token = tokens.LT(2);
        if (supplied.equals(token.getText())) {
            return document;
        }
        int sourceStartOffset = node.sourceStartOffset() + token.getStartIndex();
        int sourceEndExclusive = node.sourceStartOffset() + token.getStopIndex() + 1;
        if (isSnmpValue(definition(document, node))) {
            sourceEndExclusive = node.terminatorOffset();
        }
        if (token.getType() == tlvParser.SEMICOLON) {
            sourceEndExclusive = sourceStartOffset;
            supplied = " " + supplied;
        }
        Document changed = parse(document.path(), document.text().substring(0, sourceStartOffset) + supplied
                + document.text().substring(sourceEndExclusive), document.diskHash(), document.reference());
        if (count(changed.root()) != count(document.root())) {
            throw new InspectionException("Editing must preserve the number of TLV entries.");
        }
        return changed.withOrigin(document.origin());
    }

    /**
     * Checks whether a top-level TLV can be copied within its instance limit.
     *
     * @param document non-null document snapshot to inspect or modify
     * @param node selected TLV snapshot; may be null for capability checks
     * @return true when a complete additional copy is allowed
     */
    public boolean canDuplicate(Document document, Node node) {
        if (document == null || !canDelete(document, node)
                || !document.root().childTlvs().contains(node)) {
            return false;
        }
        int maxInstances = definition(document, node).maxInstances();
        return maxInstances < 0 || document.root().childTlvs().stream()
                .filter(child -> child.tlvPath().equals(node.tlvPath())).count() < maxInstances;
    }

    /**
     * Copies a top-level TLV and its complete subtree after the original.
     *
     * <p>Preserves the source subtree text and reparses an independent document tree.
     *
     * @param document non-null document snapshot to inspect or modify
     * @param node selected TLV snapshot; may be null for capability checks
     * @return new document containing the duplicate
     * @throws InspectionException if duplication is disallowed or the resulting source is invalid
     */
    public Document duplicate(Document document, Node node) throws InspectionException {
        if (!canDuplicate(document, node)) {
            throw new InspectionException("This top-level TLV cannot be duplicated or has reached its instance limit.");
        }
        String text = document.text();
        String newline = text.contains("\r\n") ? "\r\n" : "\n";
        String addition = newline + "    " + text.substring(node.sourceStartOffset(), node.sourceEndExclusive());
        return parse(document.path(), text.substring(0, node.sourceEndExclusive()) + addition
                + text.substring(node.sourceEndExclusive()), document.diskHash(), document.reference())
                .withOrigin(document.origin());
    }

    /**
     * Removes a TLV and its descendants while preserving valid parent syntax.
     *
     * <p>An emptied non-root parent becomes an Oscar placeholder statement.
     *
     * @param document non-null document snapshot to inspect or modify
     * @param node selected TLV snapshot; may be null for capability checks
     * @return reparsed document with the selected subtree removed
     * @throws InspectionException if deletion is disallowed or the resulting source is invalid
     */
    public Document delete(Document document, Node node) throws InspectionException {
        if (!canDelete(document, node)) {
            throw new InspectionException("This TLV cannot be deleted.");
        }
        Node parent = parentOf(document.root(), node);
        String text = document.text();
        if (!parent.tlvPath().isEmpty() && parent.childTlvs().size() == 1) {
            // Oscar requires at least one child between braces. Convert the empty
            // parent to its supported 'Parent;' form, retaining outside comments.
            var tokens = parser(text.substring(parent.sourceStartOffset(), parent.sourceEndExclusive())).getTokenStream();
            int opening = parent.sourceStartOffset() + tokens.LT(2).getStartIndex();
            text = text.substring(0, opening) + text.substring(opening + 1, node.sourceStartOffset())
                    + text.substring(node.sourceEndExclusive(), parent.terminatorOffset()) + ";" + text.substring(parent.terminatorOffset() + 1);
        } else {
            text = text.substring(0, node.sourceStartOffset()) + text.substring(node.sourceEndExclusive());
        }
        return parse(document.path(), text, document.diskHash(), document.reference()).withOrigin(document.origin());
    }

    /**
     * Counts descendant TLV entries beneath a node.
     *
     * <p>The supplied node itself is excluded from the count.
     *
     * @param node non-null TLV snapshot
     * @return total number of descendants
     */
    public static int count(Node node) {
        return node.childTlvs().stream().mapToInt(child -> 1 + count(child)).sum();
    }

    /**
     * Reads a destination fingerprint for overwrite-conflict detection.
     *
     * <p>Rejects symbolic-link destinations and leaves files unchanged.
     *
     * @param path source or destination filesystem path; must not be null
     * @return SHA-256 hexadecimal digest, or null when the destination does not exist
     * @throws InspectionException if the destination cannot be safely read or is a symbolic link
     */
    public String destinationHash(Path path) throws InspectionException {
        try {
            if (Files.isSymbolicLink(path)) {
                throw new IOException("Symbolic link destination");
            }
            if (!Files.exists(path)) {
                return null;
            }
            var digest = MessageDigest.getInstance("SHA-256");
            try (var input = new java.security.DigestInputStream(Files.newInputStream(path), digest)) {
                input.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        } catch (IOException error) {
            throw new InspectionException("Cannot check the destination file.", error);
        }
    }

    /**
     * Atomically saves UTF-8 configuration text after conflict checks.
     *
     * <p>Protects the imported binary origin and refreshes the saved text identity.
     *
     * @param document non-null document snapshot to inspect or modify
     * @param destination non-null destination file path
     * @param expectedHash expected destination SHA-256, or null when no file should exist
     * @return document snapshot with the saved path and disk hash
     * @throws InspectionException if encoding, destination protection, conflict checks, or the atomic write fail
     */
    public Document save(Document document, Path destination, String expectedHash) throws InspectionException {
        Path target = destination.toAbsolutePath().normalize();
        rejectBinaryOrigin(document, target);
        byte[] bytes = document.text().getBytes(StandardCharsets.UTF_8);
        saveBytes(target, bytes, expectedHash);
        return new Document(target, document.text(), document.configurationType(), document.root(), hash(bytes), document.reference(), document.origin());
    }

    /**
     * Reports whether the document tree is backed by successfully encoded TLVs.
     *
     * @param document current document snapshot
     * @return false for empty drafts and reference or incomplete placeholders
     */
    public boolean hasBinaryTree(Document document) {
        return !document.reference() && !document.root().childTlvs().isEmpty() && !hasPlaceholder(document.root());
    }

    Document parseDecodedTree(String text, FileType configurationType) throws InspectionException {
        Token header = parser(text).getTokenStream().LT(1);
        String normalized = text.substring(0, header.getStartIndex()) + configurationType.header()
                + text.substring(header.getStopIndex() + 1);
        return parseSource(null, normalized, null, false);
    }

    Document openCompiledText(Path path, String text, byte[] sourceBytes, byte[] encoded) throws InspectionException {
        Document source = parseSource(path.toAbsolutePath().normalize(), text, hash(sourceBytes), false);
        return new BinaryTreeProjection(this).project(source, encoded);
    }

    Document openText(Path path, byte[] bytes) throws InspectionException {
        try {
            String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            if (text.indexOf('\0') >= 0) {
                throw new InspectionException("Expected UTF-8 configuration text.");
            }
            String beginning = text.startsWith("\uFEFF") ? text.substring(1) : text;
            return parse(path.toAbsolutePath().normalize(), text, hash(bytes), beginning.startsWith(REFERENCE_MARKER)
                    || beginning.startsWith(LEGACY_REFERENCE_MARKER));
        } catch (java.nio.charset.CharacterCodingException error) {
            throw new InspectionException("Expected UTF-8 configuration text.");
        }
    }

    Document decoded(String text, FileType configurationType, BinaryOrigin origin) throws InspectionException {
        Token header = parser(text).getTokenStream().LT(1);
        String normalized = text.substring(0, header.getStartIndex()) + configurationType.header()
                + text.substring(header.getStopIndex() + 1);
        return parse(null, normalized, null, false).withOrigin(origin);
    }

    void rejectBinaryOrigin(Document document, Path target) throws InspectionException {
        if (document.origin() == null) {
            return;
        }
        Path source = document.origin().path();
        try {
            if (source.equals(target) || Files.exists(source) && Files.exists(target) && Files.isSameFile(source, target)) {
                throw new InspectionException("Choose a different destination; preserve the imported binary file.");
            }
        } catch (IOException error) {
            throw new InspectionException("Cannot check the imported binary destination.", error);
        }
    }

    /** Shared atomic writer for text documents and Oscar's encoded output. */
    void saveBytes(Path destination, byte[] bytes, String expectedHash) throws InspectionException {
        Path target = destination.toAbsolutePath().normalize();
        Path temporary = null;
        try {
            if (!Objects.equals(destinationHash(target), expectedHash)) {
                throw new InspectionException("The destination changed on disk. Use Save As or reopen it.");
            }
            temporary = Files.createTempFile(target.getParent(), ".oscar-ui-", ".tmp");
            Files.write(temporary, bytes);
            if (Files.exists(target) && Files.getFileStore(target).supportsFileAttributeView("posix")) {
                Files.setPosixFilePermissions(temporary, Files.getPosixFilePermissions(target));
            }
            if (!Objects.equals(destinationHash(target), expectedHash)) {
                throw new InspectionException("The destination changed during saving.");
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException error) {
            throw new InspectionException("Cannot safely save the file. Check its path and permissions.", error);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) {}
            }
        }
    }

    private boolean isSnmpValue(TlvDefinition definition) {
        return definition.codec().equals("oid")
                && Set.of("Snmp11", "Snmp64").contains(definition.name());
    }

    private Map<RegistryFamily, Catalog> discoverCatalogs() throws IOException {
        var directory = TlvRegistry.class.getResource("/tlv-registry/");
        if (directory == null) {
            throw new IOException("Missing registry directory");
        }
        var names = new TreeSet<String>();
        if (directory.openConnection() instanceof JarURLConnection connection) {
            try (var jar = new java.util.jar.JarFile(Path.of(java.net.URI.create(connection.getJarFileURL().toString())).toFile())) {
                jar.stream().map(java.util.jar.JarEntry::getName)
                        .filter(name -> name.startsWith("tlv-registry/") && name.endsWith(".json"))
                        .forEach(names::add);
            }
        } else if (directory.getProtocol().equals("file")) {
            try (var files = Files.list(Path.of(java.net.URI.create(directory.toString())))) {
                files.filter(path -> path.toString().endsWith(".json"))
                        .forEach(path -> names.add("tlv-registry/" + path.getFileName()));
            }
        } else {
        throw new IOException("Unsupported registry location");
    }
        var result = new LinkedHashMap<RegistryFamily, Catalog>();
        var mapper = new ObjectMapper();
        for (String name : names) {
            try (var input = TlvRegistry.class.getResourceAsStream("/" + name)) {
                var metadata = mapper.readTree(input);
                // Embedded service catalogs are resolved by OSCAR through their base family.
                if (metadata.has("parentPath")) {
                    continue;
                }
                String family = metadata.get("family").asText();
                result.put(new RegistryFamily(family), new Catalog(family, metadata.get("edition").asText(), TlvRegistry.forFamily(family)));
            }
        }
        return Map.copyOf(result);
    }

    private Document parse(Path path, String text, String diskHash, boolean reference) throws InspectionException {
        Document source = parseSource(path, text, diskHash, reference);
        if (!hasBinaryTree(source)) {
            return source;
        }
        return new BinaryTreeProjection(this).project(source, OscarTextCompileCheck.compile(text));
    }

    private boolean hasPlaceholder(Node node) {
        if (!node.tlvPath().isEmpty() && node.childTlvs().isEmpty() && node.encodedValueText().isEmpty()) {
            return true;
        }
        return node.childTlvs().stream().anyMatch(this::hasPlaceholder);
    }

    private Document parseSource(Path path, String text, String diskHash, boolean reference) throws InspectionException {
        try {
            types();
            if (text.getBytes(StandardCharsets.UTF_8).length > OscarInspectionService.MAX_BYTES) {
                throw new IllegalArgumentException();
            }
            var parser = parser(text.startsWith("\uFEFF") ? " " + text.substring(1) : text);
            // Use the same enclosing grammar rule as Oscar's compiler. Calling
            // startTLV in isolation lets its SNMP wildcard consume later statements.
            var tokens = parser.getTokenStream();
            parser.configType();
            boolean emptyDraft = tokens.LA(1) == tlvParser.LBRACE
                    && tokens.LA(2) == tlvParser.RBRACE;
            tokens.seek(0);
            String header;
            Token closing;
            var nodes = new ArrayList<Node>();
            if (emptyDraft) {
                header = parser.configType().getText();
                tokens.consume();
                closing = parser.getCurrentToken();
                tokens.consume();
            } else {
                var configuration = parser.begin();
                header = configuration.configType().getText();
                closing = configuration.RBRACE().getSymbol();
                FileType parsedType = types.stream().filter(entry -> entry.header().equals(header))
                        .findFirst().orElseThrow(() -> new IllegalArgumentException("Unsupported header"));
                for (var statement : configuration.startTLV()) {
                    nodes.add(node((ParserRuleContext) statement.getChild(0), List.of(), parsedType, text, reference));
                }
            }
            FileType configurationType = types.stream().filter(entry -> entry.header().equals(header)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unsupported header"));
            if (tokens.LA(1) != Token.EOF) {
                throw new IllegalArgumentException("Trailing input");
            }
            var root = new Node(List.of(), header, "", nodes, 0, text.length(), closing.getStartIndex(), true);
            return new Document(path, text, configurationType, root, diskHash, reference);
        } catch (RuntimeException error) {
            throw new InspectionException("Oscar rejected the configuration structure or TLV names.", error);
        }
    }

    private Node node(ParserRuleContext context, List<Integer> parent, FileType configurationType, String text, boolean reference) {
        String name = context.getStart().getText();
        var registry = catalogsByFamily.get(new RegistryFamily(configurationType.family())).registry();
        var definition = registry.children(parent).stream().filter(entry -> entry.name().equalsIgnoreCase(name))
            .findFirst().orElse(null);
        boolean namedPlaceholder = false;
        if (definition == null) {
            String statement = text.substring(context.getStart().getStartIndex(), context.getStop().getStopIndex()).strip();
            definition = registry.children(parent).stream().filter(entry -> entry.name().equalsIgnoreCase(statement))
                    .findFirst().orElse(null);
            namedPlaceholder = definition != null;
        }
        if (definition == null && !reference) {
            throw new IllegalArgumentException("Unknown TLV");
        }
        var definitionPath = new ArrayList<>(parent);
        definitionPath.add(Node.UNMAPPED_TLV_TYPE_CODE);
        List<Integer> resolvedPath = definition == null ? List.copyOf(definitionPath) : definition.path();
        var children = new ArrayList<Node>();
        int sourceStartOffset = context.getStart().getStartIndex();
        int sourceEndExclusive = context.getStop().getStopIndex() + 1;
        int terminatorOffset = sourceEndExclusive - 1;
        String value = text.substring(context.getStart().getStopIndex() + 1, sourceEndExclusive - 1).strip();
        if (context instanceof tlvParser.SnmpContext snmp) {
            terminatorOffset = snmp.SEMICOLON().getSymbol().getStartIndex();
            sourceEndExclusive = terminatorOffset + 1;
            value = text.substring(context.getStart().getStopIndex() + 1, terminatorOffset).strip();
        }
        if (context instanceof tlvParser.EmptyTLVContext) {
            value = "";
        }
        if (context instanceof tlvParser.TlvContext scalar) {
            value = scalar.typeValue().getStart().getText();
        }
        if (context instanceof tlvParser.STlvContext scalar) {
            value = scalar.subTypeValue().getStart().getText();
        }
        if (namedPlaceholder) {
            value = "";
        }
        if (context instanceof tlvParser.SubContext container) {
            for (var child : container.subTLV()) {
                children.add(node((ParserRuleContext) child.getChild(0), resolvedPath, configurationType, text, reference));
            }
            terminatorOffset = container.RBRACE().getSymbol().getStartIndex();
            value = "";
        }
        return new Node(resolvedPath, definition == null ? name : definition.name(), value, children, sourceStartOffset, sourceEndExclusive, terminatorOffset,
            definition != null && !registry.children(resolvedPath).isEmpty());
    }

    private List<TlvDefinition> scaffoldChildren(FileType configurationType, List<Integer> path) {
        return catalogsByFamily.get(new RegistryFamily(configurationType.family())).registry().children(path).stream()
                .filter(child -> !editingIssue(configurationType, child).equals("Deferred editing scope"))
                .filter(child -> child.maxInstances() != 0).toList();
    }

    private String scaffoldBody(List<TlvDefinition> children, int depth, String newline) {
        if (children.isEmpty()) {
            return ";";
        }
        return " {" + newline + children.stream()
                .map(child -> "    ".repeat(depth + 1) + child.name() + ";")
                .collect(java.util.stream.Collectors.joining(newline))
                + newline + "    ".repeat(depth) + "}";
    }

    private Node parentOf(Node root, Node target) {
        for (Node child : root.childTlvs()) {
            if (child == target) {
                return root;
            }
            Node found = parentOf(child, target);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private boolean contains(Node root, Node target) {
        return root == target || root.childTlvs().stream().anyMatch(child -> contains(child, target));
    }

    private static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    private static tlvParser parser(String text) {
        var errors = new BaseErrorListener() {
            /**
             * Stops parsing when Oscar reports a syntax error.
             *
             * <p>Reports the source position without including token contents or secrets.
             *
             * @param recognizer ANTLR recognizer reporting the syntax error
             * @param offending offending token, which is not included in the diagnostic
             * @param line one-based source line reported by ANTLR
             * @param position zero-based position within the source line
             * @param message upstream message, intentionally omitted from the thrown diagnostic
             * @param cause ANTLR recognition failure; may be null
             * @throws IllegalArgumentException always, to stop invalid input parsing
             */
            @Override public void syntaxError(Recognizer<?, ?> recognizer, Object offending, int line,
                    int position, String message, RecognitionException cause) {
                throw new IllegalArgumentException("Invalid configuration syntax at " + line + ":" + position);
            }
        };
        var lexer = new tlvLexer(new ANTLRInputStream(text));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);
        var parser = new tlvParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(errors);
        return parser;
    }
}