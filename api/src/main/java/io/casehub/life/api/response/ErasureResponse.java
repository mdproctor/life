package io.casehub.life.api.response;

import java.time.Instant;
import java.util.UUID;

public record ErasureResponse(
        UUID erasedActorId,
        Instant erasedAt,
        int memoryRecordsErased,
        long ledgerEntriesAffected,
        boolean tokenisationEnabled
) {}
