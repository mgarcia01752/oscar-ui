/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.service;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class TextRepairServiceTest {
    @TempDir Path temporary;
    private final TextRepairService service = new TextRepairService(new OscarDocumentService());
    private static final String CLEAN = "Docsis { NetworkAccess 1; }";
    private static final String BROKEN = "Docsis { NetworkAccess 1 }";

    @Test void badFixturesHaveExpectedFindingsWithoutChangingOriginals() throws Exception {
        Path directory = Path.of("src/test/resources/lint");
        var expectations = new org.json.JSONObject(Files.readString(directory.resolve("expected-diagnostics.json")));
        for (String filename : expectations.keySet()) {
            Path fixture = directory.resolve(filename);
            byte[] before = Files.readAllBytes(fixture);
            var opening = service.open(fixture);
            assertNull(opening.document());
            var report = opening.validation().lint();
            var expected = expectations.getJSONObject(filename);
            var diagnostics = expected.getJSONArray("diagnostics");
            assertEquals(diagnostics.length(), report.diagnostics().size(), filename);
            for (int index = 0; index < diagnostics.length(); index++) {
                var diagnostic = report.diagnostics().get(index);
                assertEquals(diagnostics.getJSONObject(index).getString("code"), diagnostic.code().name());
                assertEquals(diagnostics.getJSONObject(index).getInt("line"), diagnostic.range().line());
                assertEquals(diagnostics.getJSONObject(index).getInt("column"), diagnostic.range().column());
            }
            assertEquals(expected.getBoolean("fullyScanned"), report.fullyScanned());
            assertEquals(expected.getJSONArray("statementStatuses").toList(),
                    report.statements().stream().map(statement -> statement.status().name()).toList());
            assertArrayEquals(before, Files.readAllBytes(fixture));
        }
    }

    @Test void cleanIncompleteDraftOpensAndBinaryStillOpens() throws Exception {
        var file = Files.writeString(temporary.resolve("draft.txt"), CLEAN);
        assertNotNull(service.open(file).document());
        var binary = Files.write(temporary.resolve("binary.bin"), java.util.HexFormat.of().parseHex("030101ff00"));
        assertNotNull(service.open(binary).document());
    }

    @Test void cleanLintButBadRegistryNameRemainsRepairable() throws Exception {
        var file = Files.writeString(temporary.resolve("unknown.txt"), "Docsis { NetworkAccesss 1; }");
        var result = service.open(file);
        assertNull(result.document());
        assertFalse(result.validation().lint().hasErrors());
        assertNotNull(result.validation().importFailure());
    }

    @Test void saveAsPreservesOriginalAndOverwriteUsesOriginalFingerprint() throws Exception {
        var file = Files.writeString(temporary.resolve("original.txt"), BROKEN);
        var source = service.open(file).source();
        var target = temporary.resolve("repaired.txt");
        var saved = service.save(source, CLEAN, target, null);
        assertEquals(target, saved.path());
        assertEquals(CLEAN, Files.readString(target));
        assertEquals(BROKEN, Files.readString(file));
        service.save(source, CLEAN, file, source.diskHash());
        assertEquals(CLEAN, Files.readString(file));
    }

    @Test void conflictsAndInvalidRepairsNeverWrite() throws Exception {
        var file = Files.writeString(temporary.resolve("original.txt"), BROKEN);
        var source = service.open(file).source();
        Files.writeString(file, "external change");
        assertThrows(InspectionException.class, () -> service.save(source, CLEAN, file, service.destinationHash(file)));
        assertEquals("external change", Files.readString(file));
        var target = Files.writeString(temporary.resolve("other.txt"), "keep");
        String expected = service.destinationHash(target);
        Files.writeString(target, "new external change");
        assertThrows(InspectionException.class, () -> service.save(source, CLEAN, target, expected));
        assertThrows(InspectionException.class, () -> service.save(source, BROKEN, temporary.resolve("new.txt"), null));
        assertFalse(Files.exists(temporary.resolve("new.txt")));
        assertThrows(InspectionException.class, () -> service.save(source, CLEAN, temporary.resolve("absent/new.txt"), null));
        assertEquals("new external change", Files.readString(target));
    }

    @Test void readOnlyOriginalCannotBeReplacedByAtomicSave() throws Exception {
        var file = Files.writeString(temporary.resolve("readonly.txt"), BROKEN);
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.getFileStore(file).supportsFileAttributeView("posix"));
        var source = service.open(file).source();
        var originalPermissions = Files.getPosixFilePermissions(file);
        try {
            Files.setPosixFilePermissions(file, java.nio.file.attribute.PosixFilePermissions.fromString("r--r--r--"));
            assertThrows(InspectionException.class, () -> service.save(source, CLEAN, file, source.diskHash()));
            assertEquals(BROKEN, Files.readString(file));
        } finally {
            Files.setPosixFilePermissions(file, originalPermissions);
        }
    }
}
