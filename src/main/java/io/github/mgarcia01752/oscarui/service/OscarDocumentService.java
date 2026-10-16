package io.github.mgarcia01752.oscarui.service;

import com.comcast.oscar.configurationfile.ConfigurationFileExport;
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
    private static final String REFERENCE_MARKER = "/* Oscar Desktop reference template: not deployment defaults. */";
    private static final Set<String> VALUE_CODECS = Set.of("integer", "unsigned_integer", "byte", "string",
            "string_null_terminated", "string_bits", "byte_array", "double_byte_array", "mac_address",
            "byte_array_ipv4_address", "byte_array_ipv6_address", "transport_address_ipv4_address",
            "transport_address_ipv6_address", "byte_array_inet_transport_address", "byte_array_inet_address");
    public record FileType(int id, String header, String family, String edition) {
        @Override public String toString() { return header + " (" + edition + ")"; }
    }
    public record Node(List<Integer> path, String name, String value, List<Node> children,
                       int start, int end, int close, boolean container) {
        public Node { path = List.copyOf(path); children = List.copyOf(children); }
    }
    public record Document(Path path, String text, FileType type, Node root, String diskHash,
                           boolean reference) {}
    private record Catalog(String family, String edition, TlvRegistry registry) {}
    private Map<String, Catalog> catalogs;
    private List<FileType> types;

    public synchronized List<FileType> types() throws InspectionException {
        if (types != null) return types;
        try {
            catalogs = discoverCatalogs();
            var parser = parser("");
            var supported = new ArrayList<FileType>();
            var tokens = parser.getATN().nextTokens(parser.getATN().ruleToStartState[tlvParser.RULE_configType]);
            for (int token : tokens.toList()) {
                if (token <= 0) continue;
                String literal = parser.getTokenNames()[token];
                if (!literal.startsWith("'")) continue;
                String header = literal.substring(1, literal.length() - 1);
                var context = parser(header).configType();
                var listener = new TlvConfigurationFileParser();
                listener.enterConfigType(context);
                int type = listener.getConfigurationFileType();
                if (type < 0) continue;
                var definitions = new TlvDictionary().getAllTlvDefinition(type);
                for (Catalog catalog : catalogs.values()) {
                    var roots = catalog.registry().children(List.of());
                    if (roots.size() != definitions.length()) continue;
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
                        supported.add(new FileType(type, header, catalog.family(), catalog.edition()));
                        break;
                    }
                }
            }
            if (supported.isEmpty()) throw new IOException("No supported document types");
            types = supported.stream().sorted(Comparator.comparing(FileType::header)).toList();
            return types;
        } catch (IOException | RuntimeException error) {
            throw new InspectionException("Cannot discover Oscar document types and bundled catalogs.", error);
        }
    }

    private Map<String, Catalog> discoverCatalogs() throws IOException {
        var directory = TlvRegistry.class.getResource("/tlv-registry/");
        if (directory == null) throw new IOException("Missing registry directory");
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
        } else throw new IOException("Unsupported registry location");
        var result = new LinkedHashMap<String, Catalog>();
        var mapper = new ObjectMapper();
        for (String name : names) {
            try (var input = TlvRegistry.class.getResourceAsStream("/" + name)) {
                var metadata = mapper.readTree(input);
                String family = metadata.get("family").asText();
                result.put(family, new Catalog(family, metadata.get("edition").asText(), TlvRegistry.forFamily(family)));
            }
        }
        return Map.copyOf(result);
    }

    public Document open(Path path) throws InspectionException {
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(OscarInspectionService.MAX_BYTES + 1);
            if (bytes.length == 0 || bytes.length > OscarInspectionService.MAX_BYTES) {
                throw new InspectionException("Select a nonempty configuration text file up to 1 MiB.");
            }
            String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            if (text.indexOf('\0') >= 0) throw new InspectionException("Binary files are not supported.");
            String beginning = text.startsWith("\uFEFF") ? text.substring(1) : text;
            return parse(path.toAbsolutePath().normalize(), text, hash(bytes), beginning.startsWith(REFERENCE_MARKER));
        } catch (IOException error) {
            throw new InspectionException("Cannot read UTF-8 configuration text. Check the file and permissions.", error);
        }
    }

    public Document create(FileType type, boolean allTlvs) throws InspectionException {
        if (!types().contains(type)) throw new InspectionException("Unsupported configuration type.");
        try {
            String text = type.header() + " {\n}\n";
            if (allTlvs) {
                String exported = new ConfigurationFileExport(type.id()).toPrettyPrint(ConfigurationFileExport.EXPORT_DEFAULT_TLV);
                Token header = parser(exported).getTokenStream().LT(1);
                text = REFERENCE_MARKER + "\n" + exported.substring(0, header.getStartIndex()) + type.header()
                        + exported.substring(header.getStopIndex() + 1);
            }
            return parse(null, text, null, allTlvs);
        } catch (RuntimeException error) {
            throw new InspectionException("Oscar could not generate this configuration template.", error);
        }
    }

    private Document parse(Path path, String text, String diskHash, boolean reference) throws InspectionException {
        try {
            types();
            if (text.getBytes(StandardCharsets.UTF_8).length > OscarInspectionService.MAX_BYTES) throw new IllegalArgumentException();
            var parser = parser(text.startsWith("\uFEFF") ? " " + text.substring(1) : text);
            String header = parser.configType().getText();
            FileType type = types.stream().filter(entry -> entry.header().equals(header)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unsupported header"));
            if (parser.getTokenStream().LA(1) != tlvParser.LBRACE) throw new IllegalArgumentException("Missing opening brace");
            parser.getTokenStream().consume();
            var nodes = new ArrayList<Node>();
            while (parser.getTokenStream().LA(1) != tlvParser.RBRACE
                    && parser.getTokenStream().LA(1) != Token.EOF) {
                nodes.add(node((ParserRuleContext) parser.startTLV().getChild(0), List.of(), type, text, reference));
            }
            Token closing = parser.getCurrentToken();
            if (closing.getType() != tlvParser.RBRACE) throw new IllegalArgumentException("Missing closing brace");
            parser.getTokenStream().consume();
            if (parser.getTokenStream().LA(1) != Token.EOF) throw new IllegalArgumentException("Trailing input");
            var root = new Node(List.of(), header, "", nodes, 0, text.length(), closing.getStartIndex(), true);
            return new Document(path, text, type, root, diskHash, reference);
        } catch (RuntimeException error) {
            throw new InspectionException("Oscar rejected the configuration structure or TLV names.", error);
        }
    }

    private Node node(ParserRuleContext context, List<Integer> parent, FileType type, String text, boolean reference) {
        String name = context.getStart().getText();
        var registry = catalogs.get(type.family()).registry();
        var definition = registry.children(parent).stream().filter(entry -> entry.name().equalsIgnoreCase(name))
            .findFirst().orElse(null);
        if (definition == null && !reference) throw new IllegalArgumentException("Unknown TLV");
        var definitionPath = new ArrayList<>(parent);
        definitionPath.add(-1);
        List<Integer> resolvedPath = definition == null ? List.copyOf(definitionPath) : definition.path();
        var children = new ArrayList<Node>();
        int start = context.getStart().getStartIndex();
        int end = context.getStop().getStopIndex() + 1;
        int close = end - 1;
        String value = text.substring(context.getStart().getStopIndex() + 1, end - 1).strip();
        if (context instanceof tlvParser.EmptyTLVContext) value = "";
        if (context instanceof tlvParser.TlvContext scalar) value = scalar.typeValue().getStart().getText();
        if (context instanceof tlvParser.STlvContext scalar) value = scalar.subTypeValue().getStart().getText();
        if (context instanceof tlvParser.SubContext container) {
            for (var child : container.subTLV()) {
                children.add(node((ParserRuleContext) child.getChild(0), resolvedPath, type, text, reference));
            }
            close = container.RBRACE().getSymbol().getStartIndex();
            value = "";
        }
        return new Node(resolvedPath, name, value, children, start, end, close,
            definition != null && !registry.children(resolvedPath).isEmpty());
    }

    public List<TlvDefinition> choices(Document document, Node parent) throws InspectionException {
        types();
        if (!contains(document.root(), parent) || !parent.container() || parent.path().contains(-1)) return List.of();
        return catalogs.get(document.type().family()).registry().children(parent.path()).stream()
            .filter(definition -> editingIssue(document.type(), definition).isEmpty())
                .filter(definition -> definition.maxInstances() < 0 || parent.children().stream()
                        .filter(child -> child.path().equals(definition.path())).count() < definition.maxInstances())
                .toList();
    }

    public boolean isContainer(FileType type, TlvDefinition definition) {
        return !catalogs.get(type.family()).registry().children(definition.path()).isEmpty();
    }

    public String editingIssue(FileType type, TlvDefinition definition) {
        var registry = catalogs.get(type.family()).registry();
        if (!definition.equals(registry.find(definition.path()))) return "Unmapped catalog entry";
        if (type.family().equals("docsis40")) {
            int root = definition.path().get(0);
            if (root == 65 || root >= 201 && root <= 231) return "Deferred editing scope";
            for (int size = 1; size <= definition.path().size(); size++) {
                var ancestor = registry.find(definition.path().subList(0, size));
                String name = ancestor == null ? "" : ancestor.name().toLowerCase(Locale.ROOT);
                if (name.contains("l2vpn") || name.contains("esafe") || name.contains("edocsis")) {
                    return "Deferred editing scope";
                }
            }
        }
        return isContainer(type, definition) || VALUE_CODECS.contains(definition.codec()) ? "" : "Unsupported encoding";
    }

    public String valueIssue(FileType type, TlvDefinition definition, String value) {
        String issue = editingIssue(type, definition);
        if (!issue.isEmpty()) return issue;
        if (isContainer(type, definition)) return "";
        String supplied = value.strip();
        if (supplied.isEmpty()) return "Enter a value";
        if (supplied.indexOf('\n') >= 0 || supplied.indexOf('\r') >= 0) return "Enter a single TLV value";
        if (Set.of("integer", "unsigned_integer", "byte").contains(definition.codec())) {
            try {
                var number = new BigInteger(supplied);
                if (definition.codec().equals("unsigned_integer") && (number.signum() < 0
                        || number.bitLength() > definition.lengthMax() * 8)) return "Value exceeds unsigned codec width";
            } catch (NumberFormatException error) { return "Enter an integer value"; }
        }
        try {
            var probe = parser(definition.name() + " " + supplied + ";");
            var parsed = probe.startTLV();
            if (probe.getTokenStream().LA(1) != Token.EOF || parsed.tlv() == null) return "Enter a single scalar value";
        } catch (RuntimeException error) { return "Value does not match Oscar text syntax"; }
        return "";
    }

    public Document insert(Document document, Node parent, TlvDefinition definition, String value)
            throws InspectionException {
        if (!contains(document.root(), parent) || !choices(document, parent).contains(definition)) {
            throw new InspectionException("This TLV is not available in the selected container.");
        }
        boolean container = isContainer(document.type(), definition);
        String supplied = value.strip();
        String issue = valueIssue(document.type(), definition, value);
        if (!issue.isEmpty()) throw new InspectionException(issue + ".");
        String entry = definition.name() + (container ? "" : " " + supplied) + ";";
        var probe = parser(entry);
        try {
            var parsed = probe.startTLV();
            if (probe.getTokenStream().LA(1) != Token.EOF
                    || !parsed.getStart().getText().equals(definition.name()) || parsed.sub() != null) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException error) {
            throw new InspectionException("The value does not match Oscar's text syntax.", error);
        }
        int depth = parent.path().size() + 1;
        String newline = document.text().contains("\r\n") ? "\r\n" : "\n";
        String addition = newline + "    ".repeat(depth) + entry + newline + "    ".repeat(depth - 1);
        String text;
        if (!parent.path().isEmpty() && document.text().charAt(parent.close()) == ';') {
            text = document.text().substring(0, parent.close()) + " {" + addition + "}"
                    + document.text().substring(parent.end());
        } else {
            text = document.text().substring(0, parent.close()) + addition + document.text().substring(parent.close());
        }
        Document changed = parse(document.path(), text, document.diskHash(), document.reference());
        if (count(changed.root()) != count(document.root()) + 1) {
            throw new InspectionException("A value must not insert additional TLV statements.");
        }
        return changed;
    }

    public TlvDefinition definition(Document document, Node node) {
        return catalogs.get(document.type().family()).registry().find(node.path());
    }

    public boolean canDelete(Document document, Node node) {
        if (node == null || node.path().isEmpty() || !contains(document.root(), node)) return false;
        var definition = definition(document, node);
        return definition != null && editingIssue(document.type(), definition).isEmpty()
                && node.children().stream().allMatch(child -> canDelete(document, child));
    }

    public boolean canEdit(Document document, Node node) {
        return canDelete(document, node) && !node.container() && node.children().isEmpty();
    }

    public Document replaceValue(Document document, Node node, String value) throws InspectionException {
        if (!canEdit(document, node)) throw new InspectionException("This TLV cannot be edited.");
        String supplied = value.strip();
        if (supplied.equals(node.value())) return document;
        String issue = valueIssue(document.type(), definition(document, node), supplied);
        if (!issue.isEmpty()) throw new InspectionException(issue + ".");
        // Oscar scalar values occupy one lexer token. Replace that token only,
        // leaving comments and whitespace on either side untouched.
        var tokens = parser(document.text().substring(node.start(), node.end())).getTokenStream();
        Token token = tokens.LT(2);
        int start = node.start() + token.getStartIndex();
        int end = node.start() + token.getStopIndex() + 1;
        if (token.getType() == tlvParser.SEMICOLON) {
            end = start;
            supplied = " " + supplied;
        }
        Document changed = parse(document.path(), document.text().substring(0, start) + supplied
                + document.text().substring(end), document.diskHash(), document.reference());
        if (count(changed.root()) != count(document.root())) {
            throw new InspectionException("Editing must preserve the number of TLV entries.");
        }
        return changed;
    }

    public Document delete(Document document, Node node) throws InspectionException {
        if (!canDelete(document, node)) throw new InspectionException("This TLV cannot be deleted.");
        Node parent = parentOf(document.root(), node);
        String text = document.text();
        if (!parent.path().isEmpty() && parent.children().size() == 1) {
            // Oscar requires at least one child between braces. Convert the empty
            // parent to its supported 'Parent;' form, retaining outside comments.
            var tokens = parser(text.substring(parent.start(), parent.end())).getTokenStream();
            int opening = parent.start() + tokens.LT(2).getStartIndex();
            text = text.substring(0, opening) + text.substring(opening + 1, node.start())
                    + text.substring(node.end(), parent.close()) + ";" + text.substring(parent.close() + 1);
        } else {
            text = text.substring(0, node.start()) + text.substring(node.end());
        }
        return parse(document.path(), text, document.diskHash(), document.reference());
    }

    private Node parentOf(Node root, Node target) {
        for (Node child : root.children()) {
            if (child == target) return root;
            Node found = parentOf(child, target);
            if (found != null) return found;
        }
        return null;
    }

    private boolean contains(Node root, Node target) {
        return root == target || root.children().stream().anyMatch(child -> contains(child, target));
    }

    public static int count(Node node) {
        return node.children().stream().mapToInt(child -> 1 + count(child)).sum();
    }

    public String destinationHash(Path path) throws InspectionException {
        try {
            if (Files.isSymbolicLink(path)) throw new IOException("Symbolic link destination");
            if (!Files.exists(path)) return null;
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

    public Document save(Document document, Path destination, String expectedHash) throws InspectionException {
        Path target = destination.toAbsolutePath().normalize();
        Path temporary = null;
        try {
            if (!Objects.equals(destinationHash(target), expectedHash)) {
                throw new InspectionException("The destination changed on disk. Use Save As or reopen it.");
            }
            temporary = Files.createTempFile(target.getParent(), ".oscar-ui-", ".tmp");
            Files.writeString(temporary, document.text(), StandardCharsets.UTF_8);
            if (Files.exists(target) && Files.getFileStore(target).supportsFileAttributeView("posix")) {
                Files.setPosixFilePermissions(temporary, Files.getPosixFilePermissions(target));
            }
            if (!Objects.equals(destinationHash(target), expectedHash)) {
                throw new InspectionException("The destination changed during saving.");
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return new Document(target, document.text(), document.type(), document.root(),
                    hash(document.text().getBytes(StandardCharsets.UTF_8)), document.reference());
        } catch (IOException error) {
            throw new InspectionException("Cannot safely save the text file. Check its path and permissions.", error);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) {}
            }
        }
    }

    private static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    private static tlvParser parser(String text) {
        var errors = new BaseErrorListener() {
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