/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.service;

import java.nio.file.Path;

/**
 * Immutable inspection result without UI or native dependencies.
 *
 * @param path normalized source file path
 * @param content inspected configuration text
 * @param summary syntax-inspection summary and source fingerprint
 */
public record Inspection(Path path, String content, String summary) {}
