package io.casehub.life.api.request;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

public record CreateLifeTaskRequest(
        @NotBlank String templateRef,
        @NotBlank String title,
        UUID externalActorId,   // optional — links task to a tracked ExternalActor
        Instant deadline        // optional — overrides template's default_expiry_hours
        // NOTE: candidateGroups intentionally absent — groups come from the template only.
        //       Prevents tier detection bugs in LifeSlaBreachPolicy (GE-20260522-4e806e).
) {}
