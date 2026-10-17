package io.oscarui.service;

import java.nio.file.Path;

/** Immutable result; contains no UI or native types. */
public record Inspection(Path path, String content, String summary) {}
