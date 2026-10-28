/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.service;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SessionDiagnosticsTest {
    @AfterEach void clear() { SessionDiagnostics.clear(); }

    @Test void capturesCausesWithoutMessagesPathsOrValues() {
        SessionDiagnostics.clear();
        var cause = new java.nio.file.AccessDeniedException("/private/operator/secret-map.json");
        var failure = new ExceptionInInitializerError(new IllegalStateException("community=lab-secret", cause));
        SessionDiagnostics.failure(SessionDiagnostics.Stage.BINARY_EXPORT, failure);
        String log = SessionDiagnostics.snapshot();
        assertTrue(log.contains("BINARY_EXPORT"));
        assertTrue(log.contains("ExceptionInInitializerError"));
        assertTrue(log.contains("AccessDeniedException"));
        assertTrue(log.contains("SessionDiagnosticsTest.capturesCauses"));
        assertFalse(log.contains("lab-secret"));
        assertFalse(log.contains("secret-map"));
        assertFalse(log.contains("/private"));
    }

    @Test void boundsHistoryAndClearsWithoutDisablingCapture() {
        SessionDiagnostics.clear();
        SessionDiagnostics.failure(SessionDiagnostics.Stage.FILE_READ, null);
        for (int event = 0; event < 200; event++) {
            SessionDiagnostics.failure(SessionDiagnostics.Stage.TEXT_COMPILE, null);
        }
        assertFalse(SessionDiagnostics.snapshot().contains("FILE_READ"));
        assertEquals(200, SessionDiagnostics.snapshot().lines().filter(line -> line.endsWith("TEXT_COMPILE failed")).count());
        SessionDiagnostics.clear();
        assertTrue(SessionDiagnostics.snapshot().contains("No failures recorded"));
        SessionDiagnostics.failure(SessionDiagnostics.Stage.SNMP_CACHE, null);
        assertTrue(SessionDiagnostics.snapshot().contains("SNMP_CACHE failed"));
    }

    @Test void malformedBinaryRecordsFailureWithoutInputContents() throws Exception {
        var binary = new OscarBinaryService(new OscarDocumentService());
        var path = java.nio.file.Files.createTempFile("synthetic-binary", ".cfg");
        try {
            java.nio.file.Files.write(path, new byte[] {3, 9, 1});
            assertThrows(InspectionException.class, () -> binary.openAuto(path));
            assertTrue(SessionDiagnostics.snapshot().contains("BINARY_FRAMING failed"));
            assertFalse(SessionDiagnostics.snapshot().contains(path.toString()));
        } finally {
            java.nio.file.Files.deleteIfExists(path);
        }
    }
}
