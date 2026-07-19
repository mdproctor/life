package io.casehub.life.api.response;

import io.casehub.life.api.LifeCaseStatus;
import io.casehub.life.api.LifeCaseType;
import io.casehub.life.api.LifeDomain;
import java.time.Instant;
import java.util.UUID;

public record LifeCaseDetailResponse(
        UUID caseId,
        LifeCaseType caseType,
        LifeDomain domain,
        LifeCaseStatus status,
        Instant createdAt,
        Instant completedAt,
        UUID engineCaseId
) {}
