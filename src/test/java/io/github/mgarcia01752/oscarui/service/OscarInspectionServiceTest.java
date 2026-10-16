package io.github.mgarcia01752.oscarui.service;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OscarInspectionServiceTest {
    @TempDir Path temporary;
    final OscarInspectionService service = new OscarInspectionService();
    Path input(String text) throws Exception { return Files.writeString(temporary.resolve("configuration.txt"), text); }
    @Test void inspectsRealOscarCatalogWithoutWritingFile() throws Exception {
        Path file = input("Docsis { NetworkAccess 1; MaxCPE 3; }");
        byte[] before = Files.readAllBytes(file);
        var result = service.inspect(file);
        assertTrue(result.summary().contains("2 top-level"));
        assertTrue(result.content().contains("MaxCPE 3"));
        assertArrayEquals(before, Files.readAllBytes(file));
        try (var paths = Files.list(temporary)) { assertEquals(1, paths.count()); }
    }
    @Test void acceptsDocsis40Header() throws Exception {
        assertTrue(service.inspect(input("Docsis-4.0 { NetworkAccess 1; }")).summary().contains("type 5"));
    }
    @Test void rejectsInvalidSyntaxUnknownNamesAndTrailingInput() throws Exception {
        for (String invalid : new String[]{"Docsis { NetworkAccess 1 }", "Docsis { NotAnOscarTlv 1; }", "Docsis { NetworkAccess 1; } extra"}) {
            assertThrows(InspectionException.class, () -> service.inspect(input(invalid)));
        }
    }
    @Test void rejectsMissingEmptyBinaryAndOversizedFiles() throws Exception {
        assertThrows(InspectionException.class, () -> service.inspect(temporary.resolve("missing")));
        assertThrows(InspectionException.class, () -> service.inspect(input("")));
        assertThrows(InspectionException.class, () -> service.inspect(input("\0\1\2")));
        assertThrows(InspectionException.class, () -> service.inspect(input("x".repeat(OscarInspectionService.MAX_BYTES + 1))));
    }
    @Test void reportsOptionalSnmpLimitationWithoutInvokingIt() throws Exception {
        var error = assertThrows(InspectionException.class, () -> service.inspect(input("Docsis { Snmp11 sysName.0 OctetString \"public\"; }")));
        assertTrue(error.getMessage().contains("Net-SNMP"));
        assertFalse(Files.exists(temporary.resolve("db")));
    }
}
