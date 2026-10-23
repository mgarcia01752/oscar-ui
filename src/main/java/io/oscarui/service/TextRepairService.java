/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.service;

import com.comcast.oscar.validation.TextLinter;
import com.comcast.oscar.validation.TextLintReport;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.io.IOException;

/** Read-only lint/import checks and conflict-protected saving for text repair. */
public final class TextRepairService {
    private final OscarDocumentService documents;

    /**
     * Captured source identity; the fingerprint describes the exact bytes read.
     * @param path normalized absolute source path
     * @param text original decoded UTF-8 text
     * @param diskHash SHA-256 fingerprint of the original bytes
     */
    public record Source(Path path, String text, String diskHash) {}
    /**
     * Results for one immutable text revision.
     * @param lint upstream source diagnostics
     * @param document successfully imported document, or null
     * @param importFailure import explanation, or null when import did not fail
     */
    public record Validation(TextLintReport lint, OscarDocumentService.Document document, String importFailure) {
        /**
         * Indicates whether this exact snapshot can enter the workspace.
         * @return true only after lint and normal import both succeed
         */
        public boolean accepted() {
            return document != null && !lint.hasErrors() && lint.fullyScanned();
        }
    }
    /**
     * Outcome of automatic file opening.
     * @param document imported document, or null when repair is needed
     * @param source captured text identity; null for binary inputs
     * @param validation text checks; null for binary inputs
     */
    public record Opening(OscarDocumentService.Document document, Source source, Validation validation) {}

    /**
     * Creates an adapter sharing existing import and atomic-save behavior.
     * @param documents non-null workspace document service
     */
    public TextRepairService(OscarDocumentService documents) {
        this.documents = java.util.Objects.requireNonNull(documents);
    }

    /**
     * Detects text using the existing UTF-8/control-character policy and checks it before import.
     * Binary inputs use the normal binary opening path. No files are changed.
     * @param path non-null input path
     * @return document or immutable repair snapshot
     * @throws InspectionException if reading or binary opening fails
     */
    public Opening open(Path path) throws InspectionException {
        byte[] bytes;
        try (var input = Files.newInputStream(path)) {
            bytes = input.readNBytes(OscarInspectionService.MAX_BYTES + 1);
        } catch (IOException error) {
            throw new InspectionException("Cannot read configuration file.", error);
        }
        if (bytes.length == 0 || bytes.length > OscarInspectionService.MAX_BYTES) {
            throw new InspectionException("Select a nonempty configuration up to 1 MiB.");
        }
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        } catch (java.nio.charset.CharacterCodingException binary) {
            return new Opening(new OscarBinaryService(documents).openAuto(path), null, null);
        }
        if (text.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')) {
            return new Opening(new OscarBinaryService(documents).openAuto(path), null, null);
        }
        var source = new Source(path.toAbsolutePath().normalize(), text, digest(bytes));
        var checked = validate(source, text);
        return new Opening(checked.document(), source, checked);
    }

    /**
     * Checks an immutable editor snapshot, without saving or requiring a shared secret.
     * Expected import failures remain separate from lint diagnostics. Unexpected failures propagate.
     * @param source original identity used for the prospective document
     * @param text current editor text, non-null
     * @return lint report and, if clean and importable, parsed document
     */
    public Validation validate(Source source, String text) {
        // Preserve UTF-16 coordinates while accepting the BOM already supported by normal import.
        String lintText = text.startsWith("\uFEFF") ? " " + text.substring(1) : text;
        var lint = TextLinter.lint(lintText);
        if (lint.hasErrors() || !lint.fullyScanned()) {
            return new Validation(lint, null, null);
        }
        try {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > OscarInspectionService.MAX_BYTES) {
                return new Validation(lint, null, "Text exceeds the 1 MiB UTF-8 input limit.");
            }
            byte[] encoded = OscarTextCompileCheck.compile(text);
            return new Validation(lint, documents.openCompiledText(source.path(), text, bytes, encoded), null);
        } catch (InspectionException error) {
            return new Validation(lint, null, error.getMessage());
        }
    }

    /**
     * Reads the current destination fingerprint before the user confirms replacement.
     * @param path selected destination
     * @return SHA-256 fingerprint or null for a new destination
     * @throws InspectionException if the destination cannot be checked safely
     */
    public String destinationHash(Path path) throws InspectionException {
        return documents.destinationHash(path);
    }

    /**
     * Revalidates and atomically saves a repaired snapshot, refusing changed or read-only files.
     * @param source original source identity
     * @param text validated text to save
     * @param target selected destination
     * @param expectedHash fingerprint captured before confirmation, null for new files
     * @return document associated with the saved destination
     * @throws InspectionException if validation, permissions, conflict checking or saving fails
     */
    public OscarDocumentService.Document save(Source source, String text, Path target, String expectedHash) throws InspectionException {
        Path destination = target.toAbsolutePath().normalize();
        String expectedDestinationHash = destination.equals(source.path()) ? source.diskHash() : expectedHash;
        if (Files.exists(destination) && !Files.isWritable(destination)) {
            throw new InspectionException("Destination is read-only. Choose Save As.");
        }
        try {
            if (Files.exists(destination) && Files.getFileStore(destination).supportsFileAttributeView("posix")) {
                var permissions = Files.getPosixFilePermissions(destination);
                if (permissions.stream().noneMatch(p -> p.name().endsWith("WRITE"))) {
                    throw new InspectionException("Destination is read-only. Choose Save As.");
                }
            }
        } catch (IOException error) {
            throw new InspectionException("Cannot check destination permissions.", error);
        }
        var checked = validate(new Source(destination, source.text(), source.diskHash()), text);
        if (!checked.accepted()) {
            throw new InspectionException("The current text must pass lint and import before saving.");
        }
        documents.saveBytes(destination, text.getBytes(StandardCharsets.UTF_8), expectedDestinationHash);
        return checked.document();
    }

    private static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
