package io.casehub.life.app.commitment;

import io.casehub.life.api.commitment.CommitmentOutcome;

/**
 * Internal app-layer SPI for commitment dispatch.
 * Lives in app/ only — not api/ — because context types reference JPA entities.
 * CDI collects all implementations via Instance<LifeCommitmentStrategy>.
 */
public interface LifeCommitmentStrategy {
    boolean applies(CommitmentContext context);
    CommitmentOutcome execute(CommitmentContext context);
}
