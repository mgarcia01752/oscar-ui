/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.math.BigInteger;
import java.util.regex.Pattern;

/** Manual, read-only lookup of the latest published stable OSCAR-UI release. */
public final class ReleaseUpdateService {
    public static final String UI_PROJECT = "https://github.com/mgarcia01752/oscar-ui";
    public static final String OSCAR_PROJECT = "https://github.com/Comcast/Oscar";
    private static final Pattern VERSION = Pattern.compile("v?(\\d+)\\.(\\d+)\\.(\\d+)(-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?");
    private static final int TIMEOUT_MILLIS = 10_000;
    private static final int MAX_RESPONSE_BYTES = 1_048_576;

    /**
     * Queries GitHub without credentials; call from a worker thread.
     *
     * @param installedVersion version embedded in the installed application
     * @return readable update status, with no remote HTML
     * @throws IOException when GitHub is unavailable or returns invalid metadata
     */
    public String check(String installedVersion) throws IOException {
        var connection = (HttpURLConnection) URI.create(
                "https://api.github.com/repos/mgarcia01752/oscar-ui/releases/latest").toURL().openConnection();
        connection.setConnectTimeout(TIMEOUT_MILLIS);
        connection.setReadTimeout(TIMEOUT_MILLIS);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "OSCAR-UI");
        connection.setInstanceFollowRedirects(false);
        try {
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                return "No published release is available yet.";
            }
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("GitHub returned HTTP " + responseCode + ". Try again later.");
            }
            try (var response = connection.getInputStream()) {
                byte[] payload = response.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (payload.length > MAX_RESPONSE_BYTES) {
                    throw new IOException("GitHub returned an oversized response.");
                }
                return describe(installedVersion, new String(payload, java.nio.charset.StandardCharsets.UTF_8));
            }
        } finally {
            connection.disconnect();
        }
    }

    String describe(String installedVersion, String responseJson) throws IOException {
        var release = new ObjectMapper().readTree(responseJson);
        if (release == null || !release.isObject() || !release.path("draft").isBoolean()
                || !release.path("prerelease").isBoolean() || !release.path("tag_name").isTextual()) {
            throw new IOException("GitHub returned incomplete release metadata.");
        }
        if (release.path("draft").asBoolean() || release.path("prerelease").asBoolean()) {
            throw new IOException("GitHub did not return a stable published release.");
        }
        String latestVersion = release.path("tag_name").asText();
        int comparison = compareVersions(latestVersion, installedVersion);
        if (comparison > 0) {
            return "Update available: " + latestVersion + ". Installed: " + installedVersion + ".";
        }
        if (comparison < 0) {
            return "Your installed version (" + installedVersion + ") is newer than the latest release ("
                    + latestVersion + ").";
        }
        return "You’re up to date. Latest release: " + latestVersion + ".";
    }

    private static int compareVersions(String latestVersion, String installedVersion) throws IOException {
        var latest = VERSION.matcher(latestVersion);
        var installed = VERSION.matcher(installedVersion);
        if (!latest.matches() || !installed.matches() || latest.group(4) != null) {
            throw new IOException("Cannot compare the installed version with the published release tag.");
        }
        for (int component = 1; component <= 3; component++) {
            int comparison = new BigInteger(latest.group(component)).compareTo(new BigInteger(installed.group(component)));
            if (comparison != 0) {
                return comparison;
            }
        }
        return installed.group(4) == null ? 0 : 1;
    }
}
