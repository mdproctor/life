package io.casehub.life.api.response;

import java.util.List;

public record CaseStatisticsResponse(List<CaseTypeStats> entries) {

    public record CaseTypeStats(
            String caseType,
            long total,
            long active,
            long completed,
            long failed,
            Double avgResolutionHours,
            Double p50ResolutionHours,
            Double p95ResolutionHours,
            Double completionRate) {}
}
