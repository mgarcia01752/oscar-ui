/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationTextPreviewTest {
    @Test void hidesOnlyAnnotationsAndMapsNestedSelectionAfterCrLf() {
        String source = "Docsis {\r\n DownstreamServiceFlow { /* TLV: [25]*/\r\n"
                + " ServiceFlowReference 1; /* TLV: [25.1]*/\r\n }\r\n}";
        var shown = new ConfigurationTextPreview(source, true);
        assertEquals(source.replace("\r\n", "\n"), shown.text());
        var hidden = new ConfigurationTextPreview(source, false);
        assertFalse(hidden.text().contains("/* TLV:"));
        int start = source.indexOf("ServiceFlowReference");
        int end = source.indexOf(';', start) + 1;
        assertEquals("ServiceFlowReference 1;", hidden.text().substring(
                hidden.displayOffset(start), hidden.displayOffset(end)));
    }

    @Test void keepsQuotedValuesAndUnrelatedComments() {
        String source = "Docsis { /* user note */ SoftwareUpgradeFilename \"/* TLV: [3]*/\"; }";
        assertEquals(source, new ConfigurationTextPreview(source, false).text());
    }
}
