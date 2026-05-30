package io.casehub.life.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

public record OversightGateRequest(
        @NotNull Instant deadline,
        @NotNull @Valid CreateLifeTaskRequest pendingTask,
        @NotNull BigDecimal amountThreshold,
        @NotBlank String purchaseCategory
) {}
