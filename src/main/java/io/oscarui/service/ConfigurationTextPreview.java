package io.oscarui.service;

import java.util.regex.Pattern;

/** Display-only filtering of Oscar comments; never generates TLV annotations. */
public final class ConfigurationTextPreview {
    private static final Pattern TLV_COMMENT = Pattern.compile("/\\*\\s*TLV:\\s*\\[\\d+(?:\\.\\d+)*]\\s*\\*/");
    private final String text;
    private final int[] displayOffsets;

    /**
     * Builds a preview while retaining a mapping from original UTF-16 offsets.
     * Only Oscar-style comments outside quoted values may be hidden; quoted values
     * and other comments remain intact. CRLF is normalized for QTextDocument.
     *
     * @param source original configuration text
     * @param showTlvComments true to retain all original comments
     */
    public ConfigurationTextPreview(String source, boolean showTlvComments) {
        var hidden = new boolean[source.length()];
        if (!showTlvComments) {
            hideComments(source, hidden);
        }
        displayOffsets = new int[source.length() + 1];
        var preview = new StringBuilder();
        for (int offset = 0; offset < source.length(); offset++) {
            displayOffsets[offset] = preview.length();
            boolean crlfStart = source.charAt(offset) == '\r' && offset + 1 < source.length()
                    && source.charAt(offset + 1) == '\n';
            if (!hidden[offset] && !crlfStart) {
                preview.append(source.charAt(offset));
            }
        }
        displayOffsets[source.length()] = preview.length();
        text = preview.toString();
    }

    /**
     * Returns text for the read-only Qt preview.
     *
     * @return source with optional comment suppression and normalized CRLF
     */
    public String text() {
        return text;
    }

    /**
     * Maps a source selection boundary to the displayed text.
     *
     * @param sourceOffset UTF-16 boundary within the original source
     * @return corresponding UTF-16 boundary in the preview
     */
    public int displayOffset(int sourceOffset) {
        return displayOffsets[sourceOffset];
    }

    private static void hideComments(String source, boolean[] hidden) {
        boolean quoted = false;
        for (int offset = 0; offset < source.length(); offset++) {
            char character = source.charAt(offset);
            if (quoted && character == '\\') {
                offset++;
                continue;
            }
            if (character == '"') {
                quoted = !quoted;
            }
            if (!quoted && source.startsWith("/*", offset)) {
                int commentEnd = source.indexOf("*/", offset + 2);
                if (commentEnd < 0) {
                    break;
                }
                if (TLV_COMMENT.matcher(source.substring(offset, commentEnd + 2)).matches()) {
                    java.util.Arrays.fill(hidden, offset, commentEnd + 2, true);
                }
                offset = commentEnd + 1;
            }
        }
    }
}
