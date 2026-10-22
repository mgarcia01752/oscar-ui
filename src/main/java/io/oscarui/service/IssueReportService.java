/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/** Builds a user-reviewed GitHub issue draft from an explicit environment allowlist. */
public final class IssueReportService {
    private IssueReportService() {}

    /**
     * Builds a browser URL without contacting GitHub or submitting an issue.
     *
     * @param build filtered application version metadata
     * @param runtime JVM system properties; only OS and Java version fields are read
     * @return encoded GitHub issue URL containing environment details and empty report sections
     */
    public static String issueUrl(Properties build, Properties runtime) {
        String body = "## Environment\n"
                + "- OSCAR-UI: " + build.getProperty("ui.version", "unknown") + "\n"
                + "- OSCAR: " + build.getProperty("oscar.version", "unknown") + "\n"
                + "- Qt / Qt Jambi (bundled): " + build.getProperty("qt.version", "unknown") + "\n"
                + "- OS: " + runtime.getProperty("os.name", "unknown") + "\n"
                + "- OS version: " + runtime.getProperty("os.version", "unknown") + "\n"
                + "- Architecture: " + runtime.getProperty("os.arch", "unknown") + "\n"
                + "- Java: " + runtime.getProperty("java.version", "unknown") + "\n\n"
                + "## Steps to reproduce\n\n1. \n\n"
                + "## Expected behavior\n\n"
                + "## Actual behavior\n\n"
                + "## Screenshots (optional)\n\n"
                + "Please remove secrets and customer data before adding screenshots or files.\n";
        return ReleaseUpdateService.UI_PROJECT + "/issues/new?title="
                + URLEncoder.encode("[Bug] ", StandardCharsets.UTF_8)
                + "&body=" + URLEncoder.encode(body, StandardCharsets.UTF_8);
    }
}
