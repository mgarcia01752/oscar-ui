/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.service;

import com.comcast.oscar.configurationfile.ConfigurationFileImport;
import com.comcast.oscar.dictionary.TlvRegistry;
import com.comcast.oscar.tlv.TlvBuilder;
import com.comcast.oscar.tlv.TlvException;
import com.comcast.oscar.utilities.HexString;
import io.oscarui.service.OscarDocumentService.Document;
import io.oscarui.service.OscarDocumentService.Node;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

/** Encodes the selected source subtree through Oscar without compiling file MICs. */
public final class SelectedTlvHexService {
    /**
     * One encoded TLV and its ordered descendants.
     *
     * @param name registry display name
     * @param description Oscar registry description
     * @param hex complete type, length, and value bytes in hexadecimal
     * @param children immediate children in source order
     */
    public record HexNode(String name, String description, String hex, List<HexNode> children) {
        /**
         * Creates an immutable row tree.
         *
         * @param name TLV name
         * @param description Oscar registry description
         * @param hex complete encoded TLV bytes
         * @param children ordered child rows, copied defensively
         */
        public HexNode {
            children = List.copyOf(children);
        }
    }

    /**
     * Encodes the selected instance and its descendants from current source.
     * Uses Oscar's import, length-width metadata, byte slicing, and hex formatter.
     * Does not save files, read secrets, or regenerate integrity fields.
     *
     * @param document current document snapshot
     * @param selected exact node instance from that snapshot; not the document root
     * @return selected TLV with complete hex and ordered child rows
     * @throws InspectionException if selection or encoding cannot be represented faithfully
     */
    public HexNode inspect(Document document, Node selected) throws InspectionException {
        var ancestors = new ArrayList<Node>();
        if (selected == null || selected.tlvPath().isEmpty() || !find(document.root(), selected, ancestors)) {
            throw new InspectionException("Select a TLV to view its hex representation.");
        }
        try {
            Configurator.setLevel("com.comcast.oscar", Level.OFF);
            var registry = TlvRegistry.forFamily(document.configurationType().family());
            String source = document.text().substring(selected.sourceStartOffset(), selected.sourceEndExclusive());
            // Only wrap the selected instance. Unrelated siblings never affect its encoding.
            for (int index = ancestors.size() - 2; index > 0; index--) {
                source = ancestors.get(index).tlvName() + " { " + source + " }";
            }
            var imported = new ConfigurationFileImport(new StringBuilder(
                    document.configurationType().header() + " { " + source + " }"));
            if (imported.getConfigurationFileType() != document.configurationType().id()) {
                throw new InspectionException("Oscar resolved a different configuration type.");
            }
            byte[] encoded = imported.toByteArray();
            for (int index = 1; index < ancestors.size() - 1; index++) {
                var definition = registry.find(ancestors.get(index).tlvPath());
                validate(encoded, definition.type(), definition.lengthBytes());
                encoded = TlvBuilder.getTlvValue(encoded, 0, definition.lengthBytes());
            }
            return describe(selected, encoded, registry);
        } catch (RuntimeException | LinkageError | TlvException exception) {
            throw new InspectionException("Oscar could not encode this TLV. Check its values and child TLVs.");
        }
    }

    private HexNode describe(Node node, byte[] encoded, TlvRegistry registry) throws TlvException, InspectionException {
        var definition = registry.find(node.tlvPath());
        if (definition == null) {
            throw new InspectionException("This TLV has no supported Oscar definition.");
        }
        validate(encoded, definition.type(), definition.lengthBytes());
        var children = new ArrayList<HexNode>();
        if (!node.childTlvs().isEmpty()) {
            byte[] payload = TlvBuilder.getTlvValue(encoded, 0, definition.lengthBytes());
            int childOffset = 0;
            for (Node child : node.childTlvs()) {
                var childDefinition = registry.find(child.tlvPath());
                if (childDefinition == null || childOffset >= payload.length) {
                    throw new InspectionException("Oscar could not preserve every selected child TLV.");
                }
                int childEnd = TlvBuilder.nextTLVIndex(payload, childOffset, childDefinition.lengthBytes());
                if (childEnd <= childOffset || childEnd > payload.length) {
                    throw new InspectionException("Oscar returned an invalid child TLV length.");
                }
                children.add(describe(child, Arrays.copyOfRange(payload, childOffset, childEnd), registry));
                childOffset = childEnd;
            }
            if (childOffset != payload.length) {
                throw new InspectionException("Oscar returned unexpected child TLV bytes.");
            }
        }
        return new HexNode(node.tlvName(), definition.description(), new HexString(encoded).toString(":"), children);
    }

    private void validate(byte[] encoded, int tlvTypeCode, int lengthBytes) throws TlvException, InspectionException {
        if (encoded.length < TlvBuilder.TLV_TYPE_OVERHEAD + lengthBytes
                || Byte.toUnsignedInt(encoded[0]) != tlvTypeCode
                || TlvBuilder.nextTLVIndex(encoded, 0, lengthBytes) != encoded.length) {
            throw new InspectionException("Oscar did not produce exactly the selected TLV instance.");
        }
    }

    private boolean find(Node current, Node selected, List<Node> ancestors) {
        ancestors.add(current);
        if (current == selected) {
            return true;
        }
        for (Node child : current.childTlvs()) {
            if (find(child, selected, ancestors)) {
                return true;
            }
        }
        ancestors.removeLast();
        return false;
    }
}
