package io.oscarui.service;

public final class InspectionException extends Exception {
    /**
     * Creates a caller-facing configuration operation failure.
     *
     * <p>The message must be safe to display without exposing configuration secrets.
     *
     * @param message safe diagnostic text for the caller
     */
    public InspectionException(String message) { super(message); }
    /**
     * Creates a caller-facing configuration operation failure.
     *
     * <p>The message must be safe to display without exposing configuration secrets.
     *
     * @param message safe diagnostic text for the caller
     * @param cause underlying failure; may be null
     */
    public InspectionException(String message, Throwable cause) { super(message, cause); }
}
