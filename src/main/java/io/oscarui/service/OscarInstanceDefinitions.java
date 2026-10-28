/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.service;

import com.comcast.oscar.configurationfile.ConfigurationFile;
import com.comcast.oscar.dictionary.Dictionary;
import com.comcast.oscar.dictionary.TlvDefinition;
import com.comcast.oscar.dictionary.TlvRegistry;
import com.comcast.oscar.tlv.DocsisVendorExtension;
import io.oscarui.service.OscarDocumentService.FileType;
import io.oscarui.service.OscarDocumentService.Node;
import java.util.ArrayList;
import java.util.List;

/** Adapts OSCAR-owned opaque instance metadata without changing its shared registry. */
final class OscarInstanceDefinitions {
    private static final int UNRESTRICTED_INSTANCES = -1;

    private OscarInstanceDefinitions() {}

    static TlvDefinition resolve(FileType type, TlvRegistry registry, List<Integer> parent, String name) {
        var standard = registry.children(parent).stream()
                .filter(entry -> entry.name().equalsIgnoreCase(name)).findFirst().orElse(null);
        return standard != null ? standard : opaque(type, registry, parent, name);
    }

    static TlvDefinition resolve(FileType type, TlvRegistry registry, Node node) {
        if (node.tlvPath().isEmpty()) {
            return null;
        }
        var definition = resolve(type, registry,
                node.tlvPath().subList(0, node.tlvPath().size() - 1), node.tlvName());
        return definition != null && definition.path().equals(node.tlvPath()) ? definition : null;
    }

    static TlvDefinition opaque(FileType type, TlvRegistry registry, List<Integer> parent, String name) {
        if (type.id() != ConfigurationFile.DOCSIS_VER_31 && type.id() != ConfigurationFile.DOCSIS_VER_40
                || registry.find(parent) == null) {
            return null;
        }
        var metadata = DocsisVendorExtension.rawDefinition(registry.template(parent), name);
        if (metadata == null) {
            return null;
        }
        var path = new ArrayList<>(parent);
        path.add(metadata.getInt(Dictionary.TYPE));
        return new TlvDefinition(path, metadata.getString(Dictionary.TLV_NAME),
                metadata.getString(Dictionary.DATA_TYPE), metadata.getInt(Dictionary.LENGTH_MIN),
                metadata.getInt(Dictionary.LENGTH_MAX), metadata.getInt(Dictionary.BYTE_LENGTH),
                metadata.getString(Dictionary.SUPPORTED_VERSIONS), UNRESTRICTED_INSTANCES,
                metadata.getString(Dictionary.TLV_DESCRIPTION));
    }
}
