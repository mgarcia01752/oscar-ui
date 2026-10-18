package io.oscarui.service;

import com.comcast.oscar.configurationfile.ConfigurationFileExport;
import com.comcast.oscar.dictionary.TlvRegistry;
import com.comcast.oscar.tlv.TlvBuilder;
import com.comcast.oscar.tlv.TlvVariableBinding;
import com.comcast.oscar.tlv.TlvException;
import io.oscarui.service.OscarDocumentService.Document;
import io.oscarui.service.OscarDocumentService.Node;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Projects encoded instances into editable rows without replacing original source. */
final class BinaryTreeProjection {
    private final OscarDocumentService documents;

    BinaryTreeProjection(OscarDocumentService documents) {
        this.documents = documents;
    }

    Document project(Document source, byte[] encoded) throws InspectionException {
        try {
            var registry = TlvRegistry.forFamily(source.configurationType().family());
            var decoded = new ArrayList<Node>();
            // Export one physical instance at a time: Oscar's whole-file exporter
            // groups repeated types, which must not reorder the user's source.
            for (int byteOffset = 0; byteOffset < encoded.length;) {
                int tlvTypeCode = Byte.toUnsignedInt(encoded[byteOffset]);
                var definition = registry.find(List.of(tlvTypeCode));
                if (definition == null) {
                    throw new InspectionException("OSCAR encoded an unmapped TLV.");
                }
                int endOffset = TlvBuilder.nextTLVIndex(encoded, byteOffset, definition.lengthBytes());
                if (endOffset <= byteOffset || endOffset > encoded.length) {
                    throw new InspectionException("OSCAR returned a truncated TLV.");
                }
                var builder = new TlvBuilder();
                builder.add(new TlvVariableBinding(Arrays.copyOfRange(encoded, byteOffset, endOffset),
                        Map.of(tlvTypeCode, definition.lengthBytes())));
                var exporter = new ConfigurationFileExport(builder, source.configurationType().id());
                exporter.setDotTextOIDOutputFormat(false);
                String decodedText = exporter.toPrettyPrint(ConfigurationFileExport.EXPORT_FOUND_TLV, false);
                decoded.addAll(documents.parseDecodedTree(decodedText, source.configurationType()).root().childTlvs());
                byteOffset = endOffset;
            }
            var children = bind(encoded, source.root().childTlvs(), decoded, registry);
            Node original = source.root();
            var root = new Node(original.tlvPath(), original.tlvName(), "", children,
                    original.sourceStartOffset(), original.sourceEndExclusive(), original.terminatorOffset(), true);
            return new Document(source.path(), source.text(), source.configurationType(), root,
                    source.diskHash(), source.reference(), source.origin());
        } catch (RuntimeException | TlvException exception) {
            throw new InspectionException("OSCAR could not map the binary TLVs to the source text.", exception);
        }
    }

    private List<Node> bind(byte[] encoded, List<Node> sourceNodes, List<Node> decodedNodes,
                            TlvRegistry registry) throws InspectionException, TlvException {
        var remainingSource = new ArrayList<>(sourceNodes);
        var remainingDecoded = new ArrayList<>(decodedNodes);
        var result = new ArrayList<Node>();
        int byteOffset = 0;
        while (byteOffset < encoded.length) {
            int tlvTypeCode = Byte.toUnsignedInt(encoded[byteOffset]);
            Node source = remainingSource.stream()
                    .filter(candidate -> candidate.tlvPath().getLast() == tlvTypeCode).findFirst()
                    .orElseThrow(() -> new InspectionException("OSCAR returned an unmatched binary TLV."));
            remainingSource.remove(source);
            var definition = registry.find(source.tlvPath());
            Node decoded = remainingDecoded.stream().filter(candidate -> candidate.tlvPath().equals(source.tlvPath()))
                    .findFirst().orElseThrow(() -> new InspectionException("OSCAR omitted a decoded TLV instance."));
            remainingDecoded.remove(decoded);
            int endOffset = TlvBuilder.nextTLVIndex(encoded, byteOffset, definition.lengthBytes());
            if (endOffset <= byteOffset || endOffset > encoded.length) {
                throw new InspectionException("OSCAR returned a truncated child TLV.");
            }
            List<Node> children = List.of();
            if (source.isContainer()) {
                children = bind(TlvBuilder.getTlvValue(encoded, byteOffset, definition.lengthBytes()),
                        source.childTlvs(), decoded.childTlvs(), registry);
            }
            result.add(new Node(decoded.tlvPath(), decoded.tlvName(), decoded.encodedValueText(), children,
                    source.sourceStartOffset(), source.sourceEndExclusive(), source.terminatorOffset(), source.isContainer()));
            byteOffset = endOffset;
        }
        if (!remainingSource.isEmpty() || !remainingDecoded.isEmpty()) {
            throw new InspectionException("OSCAR returned unmatched binary TLV instances.");
        }
        return List.copyOf(result);
    }
}
