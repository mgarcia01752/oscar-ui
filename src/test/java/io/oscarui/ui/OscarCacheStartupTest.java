/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

@EnabledIfSystemProperty(named = "oscar.ui.tests", matches = "true")
class OscarCacheStartupTest {
    @TempDir Path temporary;

    @Test void importsSnmpWhenLaunchDirectoryCannotContainCache() throws Exception {
        // A file named res deterministically prevents OSCAR's old cwd/res cache,
        // even when this test is run with elevated filesystem privileges.
        Files.writeString(temporary.resolve("res"), "not a directory");
        Files.createDirectory(temporary.resolve("target"));
        Path binary = Files.write(temporary.resolve("synthetic.cfg"), HexFormat.of().parseHex(
                "0301010b14301206082b0601020101050004066c61622d636dff"));
        String classpath = Arrays.stream(System.getProperty("java.class.path")
                .split(java.io.File.pathSeparator)).map(entry -> Path.of(entry).toAbsolutePath().toString())
                .collect(Collectors.joining(java.io.File.pathSeparator));
        Path output = temporary.resolve("smoke.log");
        var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", classpath, "io.oscarui.DesktopApplication", "--smoke-test", binary.toString());
        builder.directory(temporary.toFile()).redirectErrorStream(true).redirectOutput(output.toFile());
        builder.environment().put("XDG_CACHE_HOME", temporary.resolve("cache").toString());
        builder.environment().put("QT_QPA_PLATFORM", "offscreen");
        var process = builder.start();
        try {
            assertTrue(process.waitFor(40, TimeUnit.SECONDS), "Smoke process timed out");
            assertEquals(0, process.exitValue(), Files.readString(output));
            assertTrue(Files.readString(output).contains("UI_SMOKE_OK"));
        } finally {
            process.destroyForcibly();
        }
    }
}
