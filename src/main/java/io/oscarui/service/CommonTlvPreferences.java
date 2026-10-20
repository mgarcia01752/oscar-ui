/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.service;

import static com.comcast.oscar.cablelabsdefinitions.Constants.*;
import com.comcast.oscar.dictionary.TlvDefinition;
import com.comcast.oscar.dictionary.TlvRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Set;

/** Per-user registry overlay; contains presentation choices, never configuration values. */
public final class CommonTlvPreferences {
    private static final int SCHEMA_VERSION = 1;
    private static final String COMMON_FIELD = "common_ui_tlv";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private ObjectNode profiles = MAPPER.createObjectNode();
    private Path settingsPath;

    /** Creates minimum-only defaults without accessing the filesystem. */
    public CommonTlvPreferences() {
    }

    /**
     * Loads preferences from a stable per-user location, preserving unknown profile/path entries.
     * @param path JSON settings path outside the installation directory
     * @throws IOException if the file is malformed, unsupported or unreadable
     */
    public synchronized void load(Path path) throws IOException {
        settingsPath = path.toAbsolutePath().normalize();
        profiles = readProfiles(settingsPath);
    }

    /**
     * Makes an isolated draft for a settings dialog; changes do not affect the live preference.
     * @return editable in-memory copy retaining unknown entries
     */
    public synchronized CommonTlvPreferences copy() {
        var copy = new CommonTlvPreferences();
        copy.profiles = profiles.deepCopy();
        return copy;
    }

    /**
     * Identifies the user-supplied top-level minimum enforced for UI-supported families.
     * Generated integrity fields are excluded. DPoE and PacketCable have no verified minimum here.
     * @param type registry-backed UI file type
     * @param definition entry to classify
     * @return whether the entry must remain in the Common view
     */
    public boolean isRequired(OscarDocumentService.FileType type, TlvDefinition definition) {
        return definition.path().size() == 1
                && Set.of(TlvRegistry.DOCSIS_FAMILY, TlvRegistry.DOCSIS_40_FAMILY, TlvRegistry.DPOG_FAMILY).contains(type.family())
                && Set.of(NETWORK_ACC_CTRL_OBJ, US_SERVICE_FLOW, DS_SERVICE_FLOW).contains(definition.path().getFirst());
    }

    /**
     * Resolves a saved checkbox or the minimum-only default. Child views remain unfiltered.
     * @param type UI file type, keyed by stable grammar header rather than dependency version
     * @param definition current registry entry
     * @return whether Common TLVs should show the entry
     */
    public synchronized boolean isCommon(OscarDocumentService.FileType type, TlvDefinition definition) {
        return definition.path().size() > 1 || isRequired(type, definition)
                || profiles.path(type.header()).path(pathKey(definition.path())).path(COMMON_FIELD).asBoolean(false);
    }

    /**
     * Updates a draft checkbox while retaining the required minimum.
     * @param type selected file type
     * @param definition top-level registry entry
     * @param common desired inclusion in Common TLVs
     */
    public synchronized void setCommon(OscarDocumentService.FileType type, TlvDefinition definition, boolean common) {
        profiles.withObject("/" + type.header()).withObject("/" + pathKey(definition.path()))
                .put(COMMON_FIELD, common || isRequired(type, definition));
    }

    /**
     * Atomically saves a draft and applies it only after a successful write; no path means session-only.
     * Unknown entries survive upgrades. Invalid existing files are not silently overwritten.
     * @param draft isolated settings edited by the user
     * @throws IOException if reading or replacing the saved file fails
     */
    public synchronized void save(CommonTlvPreferences draft) throws IOException {
        ObjectNode replacement = draft.copy().profiles;
        if (settingsPath != null) {
            readProfiles(settingsPath);
            var root = MAPPER.createObjectNode().put("version", SCHEMA_VERSION);
            root.set("profiles", replacement);
            Files.createDirectories(settingsPath.getParent());
            Path temporary = Files.createTempFile(settingsPath.getParent(), ".common-tlvs-", ".json");
            try {
                MAPPER.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), root);
                try {
                    Files.move(temporary, settingsPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException exception) {
                    Files.move(temporary, settingsPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
        profiles = replacement;
    }

    private static ObjectNode readProfiles(Path path) throws IOException {
        if (Files.notExists(path)) {
            return MAPPER.createObjectNode();
        }
        var root = MAPPER.readTree(path.toFile());
        if (root == null || !root.path("version").isInt() || root.path("version").intValue() != SCHEMA_VERSION
                || !root.path("profiles").isObject()) {
            throw new IOException("Unsupported Common TLV settings schema.");
        }
        var profiles = (ObjectNode) root.get("profiles");
        for (var profile : profiles) {
            if (!profile.isObject()) {
                throw new IOException("Invalid Common TLV profile.");
            }
            for (var entry : profile) {
                if (!entry.isObject() || !entry.path(COMMON_FIELD).isBoolean()) {
                    throw new IOException("Invalid Common TLV checkbox.");
                }
            }
        }
        return profiles;
    }

    private static String pathKey(List<Integer> path) {
        return path.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining("."));
    }
}
