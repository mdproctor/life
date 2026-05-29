package io.casehub.life.app.commitment;

import io.casehub.life.api.request.OversightGateRequest;

/**
 * No WorkItem — none exists until household-admin RESPONSE fulfills the gate.
 */
public record OversightContext(
        OversightGateRequest request
) implements CommitmentContext {}
