/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.service;

/** Synthetic minimum settings for tests that finalize DOCSIS binaries. */
public final class DocsisTestFixtures {
    public static final String SERVICE_FLOWS = """
            UpstreamServiceFlow { ServiceFlowReference 1; QualityOfServiceParameterSetType 07; }
            DownstreamServiceFlow { ServiceFlowReference 2; QualityOfServiceParameterSetType 07; }
            """;
    public static final String MINIMUM_DOCUMENT = "Docsis { NetworkAccess 1; " + SERVICE_FLOWS + "}";
    // Independently specified wire bytes for the two service flows above.
    public static final String SERVICE_FLOW_HEX = "180701020001060107190701020002060107";

    private DocsisTestFixtures() {
    }
}
