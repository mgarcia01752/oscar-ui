/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.service;

import com.comcast.oscar.tlv.DisassemblyOptions;
import com.comcast.oscar.tlv.TlvDisassemble;
import com.comcast.oscar.tlv.TlvDisassemblyReport;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Formats OSCAR structural diagnostics without including original values or exception text. */
final class BinaryFailureReport {
    private BinaryFailureReport() {}

    static String create(byte[] bytes, String family) {
        StringBuilder report = new StringBuilder("Binary import failed. Current document unchanged.\n");
        report.append("File size: ").append(bytes.length).append(" bytes\n");
        if (family != null && !family.equals("docsis") && !family.equals("dpoe")) {
            return report.append("OSCAR structural diagnostics do not support this family's framing yet.\n")
                    .append("No diagnostic scan performed. Original file unchanged.").toString();
        }
        report.append(family == null ? "DOCSIS framing candidate; file family was not identified.\n"
                : "Selected framing: " + family + "\n");
        report.append("Structure only; values, MICs, and editable round-trip are not validated.\n")
                .append("Values and raw hex are omitted. Unknown types assume one-byte lengths.\n\n")
                .append("OFFSET  TLV                  LENGTH  NAME / STATUS\n");
        try {
            var selectedFamily = "dpoe".equals(family)
                    ? DisassemblyOptions.Family.DPOE : DisassemblyOptions.Family.DOCSIS;
            var result = TlvDisassemble.diagnose(bytes,
                    new DisassemblyOptions(selectedFamily, OscarInspectionService.MAX_BYTES, 32, 10000, true));
            int stopOffset = append(result.entries(), report);
            if (stopOffset >= 0) {
                report.append("\nStopped at structural failure. Not inspected: ")
                        .append(stopOffset).append("..").append(bytes.length).append(" (end exclusive).\n");
            } else {
                report.append("\nReached end of input. Editable import still failed.\n");
            }
        } catch (RuntimeException | LinkageError failure) {
            report.append("\nOSCAR diagnostic scan unavailable. No payload details are displayed.\n");
        }
        return report.toString();
    }

    private static int append(List<TlvDisassemblyReport.Entry> entries, StringBuilder report) {
        for (var entry : entries) {
            String path = entry.tlvPath().stream().map(String::valueOf).collect(Collectors.joining("."));
            String indent = "  ".repeat(Math.max(0, entry.tlvPath().size() - 1));
            report.append(String.format(Locale.ROOT, "%06d  %-20s %6d  %s%s [%s]%n",
                    entry.headerOffset(), indent + path, entry.declaredLength(), indent,
                    entry.name(), entry.status()));
            switch (entry.status()) {
                case OK, UNKNOWN_TYPE, END, PADDING -> { }
                default -> {
                    report.append("  Failure offset: ").append(entry.headerOffset())
                            .append("; available value bytes: ").append(entry.endOffset() - entry.valueOffset())
                            .append('\n');
                    return entry.endOffset();
                }
            }
            int stoppedAt = append(entry.children(), report);
            if (stoppedAt >= 0) {
                return stoppedAt;
            }
        }
        return -1;
    }
}
