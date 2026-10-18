package io.oscarui.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TlvDescriptionFormatterTest {
    @Test void separatesEveryTwoSentencesAndKeepsTrailingSentence() {
        String formatted = TlvDescriptionFormatter.format("First sentence. Second sentence! Third sentence? Fourth sentence. Fifth sentence.");
        assertEquals("First sentence. Second sentence!\n\nThird sentence? Fourth sentence.\n\nFifth sentence.", formatted);
        assertEquals(formatted, TlvDescriptionFormatter.format(formatted));
    }

    @Test void keepsAbbreviationsAndTechnicalValuesWithinSentences() {
        assertEquals("Use DOCSIS 4.0, e.g. OID 1.3.6.1. Use 3.14 as the value.\n\nNext sentence.",
                TlvDescriptionFormatter.format("Use DOCSIS 4.0, e.g. OID 1.3.6.1. Use 3.14 as the value. Next sentence."));
        assertEquals("- First sentence. Second sentence. Third sentence.",
                TlvDescriptionFormatter.format("- First sentence. Second sentence. Third sentence."));
    }

    @Test void reflowsWrappedProseAndSeparatesValue() {
        assertEquals("The Primary Downstream Channel MUST use the frequency in Hz.\n\nValue: Rx Frequency",
                TlvDescriptionFormatter.format("The Primary Downstream Channel MUST use\r\nthe frequency in Hz. Value: Rx Frequency"));
    }

    @Test void preservesParagraphsAndTechnicalTokens() {
        String input = "Use DOCSIS 4.0 and OID 1.3.6.1\nwith range 0-255.\n\nNOTE: Do not change\nthe value.";
        assertEquals("Use DOCSIS 4.0 and OID 1.3.6.1 with range 0-255.\n\nNOTE: Do not change the value.",
                TlvDescriptionFormatter.format(input));
    }

    @Test void preservesEnumerationsAndExampleRows() {
        String result = TlvDescriptionFormatter.format("Control values:\n0 - allow write-access\n1 - disallow write-access\n\nExample:\nsomeTable: disallow write-access\nsomeTable.1.3: allow write-access");
        assertTrue(result.contains("0 - allow write-access\n1 - disallow write-access"));
        assertTrue(result.contains("someTable: disallow write-access\nsomeTable.1.3: allow write-access"));
        assertEquals(result, TlvDescriptionFormatter.format(result));
    }

    @Test void preservesAlignedDataAndHandlesEmptyInput() {
        assertEquals("Type  Length  Value\n1     4       0x00000001",
                TlvDescriptionFormatter.format("Type  Length  Value\n1     4       0x00000001"));
        assertEquals("", TlvDescriptionFormatter.format(null));
        assertEquals("", TlvDescriptionFormatter.format(" \r\n "));
    }
}
