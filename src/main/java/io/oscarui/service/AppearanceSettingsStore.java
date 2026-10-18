/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.*;
import java.util.Locale;
import java.util.Objects;

/** Per-user JSON display preferences; never stores document data or secrets. */
public final class AppearanceSettingsStore {
    private static final int SCHEMA_VERSION = 1;
    private final Path settingsPath;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Selects the settings file without reading or creating it.
     *
     * @param settingsPath non-null per-user JSON path
     */
    public AppearanceSettingsStore(Path settingsPath) {
        this.settingsPath = Objects.requireNonNull(settingsPath).toAbsolutePath().normalize();
    }

    /**
     * Loads a saved appearance, defaulting to System when no file exists.
     *
     * <p>Invalid settings are reported without modifying the existing file.
     *
     * @return saved appearance or SYSTEM on first use
     * @throws IOException if the file cannot be read or its schema/value is invalid
     */
    public Appearance load() throws IOException {
        if (Files.notExists(settingsPath)) {
            return Appearance.SYSTEM;
        }
        var settings = mapper.readTree(settingsPath.toFile());
        if (settings == null || !settings.isObject()
                || !settings.path("version").isInt() || settings.path("version").intValue() != SCHEMA_VERSION
                || !settings.path("appearance").isTextual()) {
            throw new IOException("Invalid appearance settings schema.");
        }
        try {
            return Appearance.valueOf(settings.get("appearance").textValue().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IOException("Unknown appearance preference.", exception);
        }
    }

    /**
     * Loads the display-masking preference; older files default to disabled.
     *
     * @return whether SNMP secret displays should be masked
     * @throws IOException if the settings file or preference is invalid
     */
    public boolean loadObfuscateSnmpSecrets() throws IOException {
        load();
        if (Files.notExists(settingsPath)) {
            return false;
        }
        var value = mapper.readTree(settingsPath.toFile()).get("obfuscateSnmpSecrets");
        if (value == null) {
            return false;
        }
        if (!value.isBoolean()) {
            throw new IOException("Invalid SNMP masking preference.");
        }
        return value.booleanValue();
    }

    /**
     * Saves the preference using a temporary file and atomic replacement where supported.
     *
     * <p>Creates the parent directory as needed; preserves the saved SNMP display-masking preference.
     *
     * @param appearance non-null preference selected by the user
     * @throws IOException if the preference cannot be written or replaced
     */
    public void save(Appearance appearance) throws IOException {
        save(appearance, loadObfuscateSnmpSecrets());
    }

    /**
     * Saves display preferences without storing secret values or custom rules.
     *
     * @param appearance non-null appearance preference
     * @param obfuscateSnmpSecrets whether SNMP displays should be masked
     * @throws IOException if settings cannot be written or replaced
     */
    public void save(Appearance appearance, boolean obfuscateSnmpSecrets) throws IOException {
        Objects.requireNonNull(appearance);
        var settings = mapper.createObjectNode();
        settings.put("version", SCHEMA_VERSION);
        settings.put("appearance", appearance.name().toLowerCase(Locale.ROOT));
        settings.put("obfuscateSnmpSecrets", obfuscateSnmpSecrets);
        Files.createDirectories(settingsPath.getParent());
        Path temporaryPath = Files.createTempFile(settingsPath.getParent(), ".settings-", ".json");
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(temporaryPath.toFile(), settings);
            try {
                Files.move(temporaryPath, settingsPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryPath, settingsPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryPath);
        }
    }
}
