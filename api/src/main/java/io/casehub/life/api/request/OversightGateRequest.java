package io.casehub.life.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Request for POST /life-oversight-gates.
 * Creates a pre-approval gate; no WorkItem exists until household-admin responds.
 */
public record OversightGateRequest(
        @NotNull Instant deadline,
        @NotNull @Valid CreateLifeTaskRequest pendingTask
) {}
