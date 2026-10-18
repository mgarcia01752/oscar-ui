/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.service;

import com.comcast.oscar.configurationfile.ConfigurationFileImport;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

/** Validates text through Oscar's unsigned TLV encoder without replacing source. */
final class OscarTextCompileCheck {
    private static final int MAX_DIAGNOSTIC_BYTES = 16_384;

    private OscarTextCompileCheck() {}

    static void validate(String source) throws InspectionException {
        compile(source);
    }

    static synchronized byte[] compile(String source) throws InspectionException {
        Configurator.setLevel("com.comcast.oscar", Level.OFF);
        var diagnostics = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        Thread owner = Thread.currentThread();
        RuntimeException runtimeFailure = null;
        LinkageError linkageFailure = null;
        byte[] encoded = null;
        // Some Oscar encoder paths print and swallow failures. Capture only this
        // thread; other threads retain their original console destinations.
        try (var capturedOut = new PrintStream(new DiagnosticOutput(owner, diagnostics, originalOut), true, StandardCharsets.UTF_8);
             var capturedErr = new PrintStream(new DiagnosticOutput(owner, diagnostics, originalErr), true, StandardCharsets.UTF_8)) {
            System.setOut(capturedOut);
            System.setErr(capturedErr);
            try {
                String input = source.startsWith("\uFEFF") ? source.substring(1) : source;
                encoded = new ConfigurationFileImport(new StringBuilder(input)).toByteArray();
            } catch (RuntimeException exception) {
                runtimeFailure = exception;
            } catch (LinkageError error) {
                linkageFailure = error;
            } finally {
                System.setOut(originalOut);
                System.setErr(originalErr);
            }
        }
        String reported = diagnostics.toString(StandardCharsets.UTF_8).lines()
                .filter(line -> !line.stripLeading().startsWith("at ") && !line.isBlank())
                .collect(java.util.stream.Collectors.joining("\n"));
        Throwable failure = runtimeFailure == null ? linkageFailure : runtimeFailure;
        if (failure != null) {
            String detail = failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage());
            reported = reported.isBlank() ? detail : reported + "\n" + detail;
        }
        if (encoded == null || !reported.isBlank()) {
            if (reported.isBlank()) {
                reported = "OSCAR returned no encoded TLV result.";
            }
            throw new InspectionException("OSCAR text compilation failed:\n" + reported);
        }
        return encoded;
    }

    private static final class DiagnosticOutput extends OutputStream {
        private final Thread owner;
        private final ByteArrayOutputStream captured;
        private final PrintStream original;

        private DiagnosticOutput(Thread owner, ByteArrayOutputStream captured, PrintStream original) {
            this.owner = owner;
            this.captured = captured;
            this.original = original;
        }

        /**
         * Captures one byte from the validation thread, forwarding other threads.
         *
         * @param value byte value supplied by PrintStream
         */
        @Override public void write(int value) {
            if (Thread.currentThread() == owner) {
                if (captured.size() < MAX_DIAGNOSTIC_BYTES) {
                    captured.write(value);
                }
            } else {
                original.write(value);
            }
        }

        /**
         * Captures bounded diagnostics without writing configuration values to logs.
         *
         * @param bytes source buffer
         * @param offset first byte in the buffer
         * @param length number of bytes to write
         */
        @Override public void write(byte[] bytes, int offset, int length) {
            if (Thread.currentThread() == owner) {
                int remaining = MAX_DIAGNOSTIC_BYTES - captured.size();
                captured.write(bytes, offset, Math.min(length, remaining));
            } else {
                original.write(bytes, offset, length);
            }
        }
    }
}
