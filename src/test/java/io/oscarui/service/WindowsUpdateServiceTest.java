/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WindowsUpdateServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void acceptsOnlyExactBaselineAndPreviousVersion() throws Exception {
        var baseline = baseline();
        var update = update();
        assertNotNull(WindowsUpdateService.compatible("1.0.0", "1.0.1", baseline, update));
        assertNull(WindowsUpdateService.compatible("0.9.9", "1.0.1", baseline, update));
        assertNull(WindowsUpdateService.compatible("1.0.1", "1.0.1", baseline, update));
        update.put("fingerprint", "b".repeat(64));
        assertNull(WindowsUpdateService.compatible("1.0.0", "1.0.1", baseline, update));
        update = update();
        update.put("productCode", "11111111-1111-1111-1111-111111111111");
        assertNull(WindowsUpdateService.compatible("1.0.0", "1.0.1", baseline, update));
    }

    @Test void rejectsInvalidPatchIdentityAndMissingMetadata() throws Exception {
        var update = update();
        ((ObjectNode) update.get("patch")).put("url", "https://example.org/update.msp");
        assertThrows(IOException.class, () -> WindowsUpdateService.compatible("1.0.0", "1.0.1", baseline(), update));
        update.set("patch", mapper.nullNode());
        assertNull(WindowsUpdateService.compatible("1.0.0", "1.0.1", baseline(), update));
        assertThrows(IOException.class, () -> WindowsUpdateService.compatible("1.0.0", "1.0.1", null, update));
    }

    @Test void developmentCheckoutCannotLaunchUpdater() {
        var service = new WindowsUpdateService();
        assertFalse(service.supported());
        assertThrows(IOException.class, () -> service.launch(null));
    }

    private ObjectNode baseline() {
        var baseline = mapper.createObjectNode();
        baseline.put("layout", 1);
        baseline.put("productCode", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        baseline.put("fingerprint", "a".repeat(64));
        return baseline;
    }

    private ObjectNode update() {
        var update = baseline();
        update.put("version", "1.0.1");
        update.put("fromVersion", "1.0.0");
        var patch = update.putObject("patch");
        patch.put("url", ReleaseUpdateService.UI_PROJECT + "/releases/download/v1.0.1/OSCAR-UI-1.0.1-windows-x64.msp");
        patch.put("digest", "sha256:" + "a".repeat(64));
        patch.put("size", 100);
        return update;
    }
}
