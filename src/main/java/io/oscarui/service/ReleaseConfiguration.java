/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.service;

import java.io.IOException;
import java.util.Properties;

/** Packaged project endpoints and filename templates shared with release tools. */
public final class ReleaseConfiguration {
    private static final Properties SETTINGS = load();

    private ReleaseConfiguration() {}

    /**
     * Resolves a project base URL from the bundled configuration.
     * @param project project key, ui or oscar
     * @param endpoint endpoint key, web, api or raw
     * @return configured HTTPS project base URL
     * @throws IllegalStateException if required configuration is absent
     */
    public static String project(String project, String endpoint) {
        return required("github." + endpoint) + "/" + required(project + ".repository");
    }

    /**
     * Formats a full installer filename without changing package metadata.
     * @param version release version without a v prefix
     * @param operatingSystem windows, ubuntu or macos
     * @param architecture x64 or arm64
     * @param extension msi, deb or dmg
     * @return filename derived from the shared template
     * @throws IllegalStateException if the naming template is absent
     */
    public static String installer(String version, String operatingSystem, String architecture, String extension) {
        return required("installer.template").replace("{version}", version).replace("{os}", operatingSystem)
                .replace("{arch}", architecture).replace("{extension}", extension);
    }

    /**
     * Formats the historical Windows patch name from the shared template.
     * @param version release version without a v prefix
     * @return MSP basename preserving the Windows update contract
     * @throws IllegalStateException if the patch template is absent
     */
    public static String patch(String version) {
        return required("patch.template").replace("{version}", version);
    }

    private static String required(String key) {
        String value = SETTINGS.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing release configuration: " + key);
        }
        return value;
    }

    private static Properties load() {
        var values = new Properties();
        try (var stream = ReleaseConfiguration.class.getResourceAsStream("/release.properties")) {
            if (stream == null) {
                throw new IllegalStateException("Missing packaged release configuration");
            }
            values.load(stream);
            return values;
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read release configuration", failure);
        }
    }
}
