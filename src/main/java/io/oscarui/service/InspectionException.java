/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.service;

public final class InspectionException extends Exception {
    /**
     * Creates a caller-facing configuration operation failure.
     *
     * <p>The message must be safe to display without exposing configuration secrets.
     *
     * @param message safe diagnostic text for the caller
     */
    public InspectionException(String message) {
        super(message);
    }
    /**
     * Creates a caller-facing configuration operation failure.
     *
     * <p>The message must be safe to display without exposing configuration secrets.
     *
     * @param message safe diagnostic text for the caller
     * @param cause underlying failure; may be null
     */
    public InspectionException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Returns an error suitable for the current display-masking preference.
     *
     * @param maskingEnabled whether sensitive diagnostic text must be hidden
     * @return original unmasked message or generic masked fallback
     */
    public String displayMessage(boolean maskingEnabled) {
        if (maskingEnabled) {
            return "The document operation failed. Details are hidden while SNMP obfuscation is enabled.";
        }
        return getMessage();
    }
}
