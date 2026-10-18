package io.oscarui.service;

public final class InspectionException extends Exception {
    private final boolean safeWhileMasked;

    /** Fixed diagnostics that never contain configuration values. */
    public enum SafeDiagnostic {
        SNMP_BINARY_EXPORT_UNSUPPORTED
    }

    /**
     * Creates a failure from an approved diagnostic containing no input values.
     *
     * @param diagnostic non-null fixed diagnostic identifier
     */
    public InspectionException(SafeDiagnostic diagnostic) {
        super(switch (diagnostic) {
            case SNMP_BINARY_EXPORT_UNSUPPORTED ->
                    "Binary export of SNMP statements is not supported yet in OSCAR-UI. "
                    + "Save as Text to preserve this configuration. Turning off obfuscation will not enable binary export.";
        });
        safeWhileMasked = true;
    }

    /**
     * Creates a caller-facing configuration operation failure.
     *
     * <p>The message must be safe to display without exposing configuration secrets.
     *
     * @param message safe diagnostic text for the caller
     */
    public InspectionException(String message) {
        super(message);
        safeWhileMasked = false;
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
        safeWhileMasked = false;
    }

    /**
     * Returns an error suitable for the current display-masking preference.
     *
     * @param maskingEnabled whether sensitive diagnostic text must be hidden
     * @return fixed safe diagnostic, original unmasked message, or generic fallback
     */
    public String displayMessage(boolean maskingEnabled) {
        if (maskingEnabled && !safeWhileMasked) {
            return "The document operation failed. Details are hidden while SNMP obfuscation is enabled.";
        }
        return getMessage();
    }
}
