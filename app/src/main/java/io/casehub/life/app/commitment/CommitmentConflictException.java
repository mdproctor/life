package io.casehub.life.app.commitment;

/**
 * Thrown when a duplicate commitment or oversight gate is detected.
 * Caught by REST resource layer and mapped to 409 Conflict.
 */
public class CommitmentConflictException extends RuntimeException {
    public CommitmentConflictException(final String message) {
        super(message);
    }
}
