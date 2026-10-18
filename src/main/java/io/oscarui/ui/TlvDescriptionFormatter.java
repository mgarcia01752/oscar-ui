package io.oscarui.ui;

import java.util.ArrayList;
import java.util.regex.Pattern;

/** Presentation-only reflow; registry text and configuration values are untouched. */
final class TlvDescriptionFormatter {
    private static final Pattern ITEM = Pattern.compile("^(?:[-*•]\\s+|\\d+[.)]\\s+|\\d+\\s+[-=:]\\s+).*");

    private static final Pattern LABEL = Pattern.compile("^[\\w.-]+(?: [\\w.-]+){0,3}:.*");

    private static final Pattern SECTION = Pattern.compile("(?i)(?<=\\S)[ \\t]+(?=(?:Value|Note|Notes|Example|Examples):)");

    private TlvDescriptionFormatter() {}

    static String format(String description) {
        if (description == null || description.isBlank()) {
            return "";
        }
        String text = description.replace("\r\n", "\n").replace('\r', '\n');
        text = SECTION.matcher(text).replaceAll("\n\n");
        var paragraphs = new ArrayList<String>();
        var paragraph = new StringBuilder();
        boolean structured = false;
        String[] sourceLines = text.split("\n", -1);
        for (int lineIndex = 0; lineIndex < sourceLines.length; lineIndex++) {
            String line = sourceLines[lineIndex].strip();
            if (line.isEmpty()) {
                flush(paragraphs, paragraph, structured);
                structured = false;
                continue;
            }
            // Preserve tables, examples, and list rows instead of flattening them.
            boolean proseLabel = line.matches("(?i)^(?:Value|Note|Notes|Example|Examples):.+");
            boolean row = ITEM.matcher(line).matches() || (LABEL.matcher(line).matches() && !proseLabel)
                    || line.contains("\t") || hasAlignedNeighbor(sourceLines, lineIndex) || line.contains("|");
            if (!row) {
                line = line.replaceAll(" {2,}", " ");
            }
            if (row != structured || proseLabel) {
                flush(paragraphs, paragraph, structured);
            }
            if (!paragraph.isEmpty()) {
                paragraph.append(row ? '\n' : ' ');
            }
            paragraph.append(line);
            structured = row;
        }
        flush(paragraphs, paragraph, structured);
        return String.join("\n\n", paragraphs);
    }

    private static boolean hasAlignedNeighbor(String[] sourceLines, int lineIndex) {
        var columnOffsets = columnOffsets(sourceLines[lineIndex]);
        if (columnOffsets.isEmpty()) {
            return false;
        }
        return lineIndex > 0 && columnOffsets.equals(columnOffsets(sourceLines[lineIndex - 1]))
                || lineIndex + 1 < sourceLines.length
                && columnOffsets.equals(columnOffsets(sourceLines[lineIndex + 1]));
    }

    private static java.util.List<Integer> columnOffsets(String sourceLine) {
        var columnOffsets = new ArrayList<Integer>();
        var columns = Pattern.compile(" {2,}(?=\\S)").matcher(sourceLine.strip());
        while (columns.find()) {
            columnOffsets.add(columns.end());
        }
        return columnOffsets;
    }

    private static void flush(ArrayList<String> paragraphs, StringBuilder paragraph, boolean structured) {
        if (!paragraph.isEmpty()) {
            paragraphs.add(structured ? paragraph.toString() : splitSentences(paragraph.toString()));
            paragraph.setLength(0);
        }
    }

    private static String splitSentences(String prose) {
        // Only punctuation followed by whitespace can end a sentence: decimals,
        // versions, OIDs, and dotted identifiers stay intact.
        var boundaries = Pattern.compile("[.!?][\\\"')\\]]*([ \\t]+)(?=[A-Z0-9\\\"'(])").matcher(prose);
        var result = new StringBuilder();
        int start = 0;
        int sentences = 0;
        while (boundaries.find()) {
            String prefix = prose.substring(0, boundaries.start(1));
            if (prefix.matches("(?is).*(?:\\b(?:e\\.g|i\\.e|etc|vs|Fig|Eq|Sec|No|Dr|Mr|Mrs)|\\b[A-Z])\\.$")) {
                continue;
            }
            if (++sentences % 2 == 0) {
                result.append(prose, start, boundaries.start(1)).append("\n\n");
                start = boundaries.end(1);
            }
        }
        return result.append(prose.substring(start)).toString();
    }
}
