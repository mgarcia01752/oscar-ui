/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.service;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class IssueReportServiceTest {
    @Test void encodesEnvironmentAndExcludesPrivateProperties() {
        var build = new Properties();
        build.setProperty("ui.version", "1.2.3+test & check");
        build.setProperty("oscar.version", "4.5.6");
        build.setProperty("qt.version", "6.8.3");
        var runtime = new Properties();
        runtime.setProperty("os.name", "Synthetic OS");
        runtime.setProperty("os.version", "release/1");
        runtime.setProperty("os.arch", "arm64");
        runtime.setProperty("java.version", "21.0.1");
        runtime.setProperty("user.home", "/private/customer");
        runtime.setProperty("user.name", "private-user");
        runtime.setProperty("java.class.path", "/private/config");
        String url = IssueReportService.issueUrl(build, runtime);
        var uri = URI.create(url);
        assertEquals("github.com", uri.getHost());
        assertEquals("/mgarcia01752/oscar-ui/issues/new", uri.getPath());
        assertEquals(2, uri.getRawQuery().split("&").length);
        String body = URLDecoder.decode(uri.getRawQuery().split("&body=", 2)[1], StandardCharsets.UTF_8);
        for (String expected : new String[]{"1.2.3+test & check", "4.5.6", "6.8.3", "Synthetic OS",
                "release/1", "arm64", "21.0.1", "Steps to reproduce", "Expected behavior", "Actual behavior"}) {
            assertTrue(body.contains(expected), expected);
        }
        assertFalse(body.contains("private"));
    }

    @Test void missingMetadataUsesUnknownWithoutGuessingVersions() {
        String url = IssueReportService.issueUrl(new Properties(), new Properties());
        assertTrue(URLDecoder.decode(url, StandardCharsets.UTF_8).contains("OSCAR-UI: unknown"));
    }
}
