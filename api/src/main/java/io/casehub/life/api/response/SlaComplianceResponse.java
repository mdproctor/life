package io.casehub.life.api.response;

import java.util.List;

public record SlaComplianceResponse(List<DomainSlaStats> entries) {

    public record DomainSlaStats(
            String domain,
            long totalWithSla,
            long breachedCount,
            Double complianceRate,
            Double avgBreachLatencyHours) {}
}
