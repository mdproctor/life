package io.casehub.life.app.service;

import io.casehub.ledger.api.model.ScoreType;
import io.casehub.ledger.runtime.model.ActorTrustScore;
import io.casehub.life.api.LifeActorIds;
import io.casehub.life.api.LifeCaseStatus;
import io.casehub.life.api.LifeDomain;
import io.casehub.life.api.response.CaseStatisticsResponse;
import io.casehub.life.api.response.CaseStatisticsResponse.CaseTypeStats;
import io.casehub.life.api.response.SlaComplianceResponse;
import io.casehub.life.api.response.SlaComplianceResponse.DomainSlaStats;
import io.casehub.life.api.response.TrustAnalyticsResponse;
import io.casehub.life.api.response.TrustAnalyticsResponse.ActorScoreSummary;
import io.casehub.life.app.entity.ExternalActor;
import io.casehub.life.app.entity.LifeCaseTracker;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.runtime.model.WorkItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class LifeAnalyticsService {

    private static final String LIFE_SCOPE_PREFIX = "casehubio/life/";

    @Inject
    @io.quarkus.hibernate.orm.PersistenceUnit("qhorus")
    EntityManager qhorusEm;

    @Transactional
    public CaseStatisticsResponse caseStatistics(String caseType) {
        List<LifeCaseTracker> trackers = caseType != null
                ? LifeCaseTracker.list("caseType", caseType)
                : LifeCaseTracker.listAll();

        Map<String, List<LifeCaseTracker>> grouped = trackers.stream()
                .collect(Collectors.groupingBy(t -> t.caseType, LinkedHashMap::new, Collectors.toList()));

        List<CaseTypeStats> entries = grouped.entrySet().stream()
                .map(e -> buildCaseTypeStats(e.getKey(), e.getValue()))
                .toList();

        return new CaseStatisticsResponse(entries);
    }

    private CaseTypeStats buildCaseTypeStats(String caseType, List<LifeCaseTracker> trackers) {
        long total = trackers.size();
        long active = trackers.stream().filter(t -> t.status == LifeCaseStatus.ACTIVE).count();
        long completed = trackers.stream().filter(t -> t.status == LifeCaseStatus.COMPLETED).count();
        long failed = trackers.stream().filter(t -> t.status == LifeCaseStatus.FAILED).count();

        List<Double> resolutionHours = trackers.stream()
                .filter(t -> t.status == LifeCaseStatus.COMPLETED && t.completedAt != null && t.createdAt != null)
                .map(t -> Duration.between(t.createdAt, t.completedAt).toMillis() / 3_600_000.0)
                .sorted()
                .toList();

        Double avg = resolutionHours.isEmpty() ? null
                : resolutionHours.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        Double p50 = percentile(resolutionHours, 0.50);
        Double p95 = percentile(resolutionHours, 0.95);

        long terminal = completed + failed;
        Double completionRate = terminal > 0 ? (double) completed / terminal : null;

        return new CaseTypeStats(caseType, total, active, completed, failed, avg, p50, p95, completionRate);
    }

    private static Double percentile(List<Double> sorted, double p) {
        if (sorted.isEmpty()) return null;
        double index = p * (sorted.size() - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        if (lower == upper) return sorted.get(lower);
        double weight = index - lower;
        return sorted.get(lower) * (1 - weight) + sorted.get(upper) * weight;
    }

    @Transactional
    public SlaComplianceResponse slaCompliance(LifeDomain domain) {
        var params = new HashMap<String, Object>();
        var conditions = new ArrayList<String>();
        conditions.add("scope LIKE :prefix");
        params.put("prefix", LIFE_SCOPE_PREFIX + "%");
        conditions.add("expiresAt IS NOT NULL");

        if (domain != null) {
            conditions.add("scope LIKE :domainScope");
            params.put("domainScope", LIFE_SCOPE_PREFIX + domain.descriptor().templateCategory() + "%");
        }

        String query = String.join(" AND ", conditions);
        List<WorkItem> items = WorkItem.list(query, params);

        Instant now = Instant.now();
        Map<String, List<WorkItem>> byDomain = items.stream()
                .collect(Collectors.groupingBy(
                        wi -> extractDomainSegment(wi.scope),
                        LinkedHashMap::new, Collectors.toList()));

        List<DomainSlaStats> entries = byDomain.entrySet().stream()
                .map(e -> buildDomainSlaStats(e.getKey(), e.getValue(), now))
                .toList();

        return new SlaComplianceResponse(entries);
    }

    private String extractDomainSegment(String scope) {
        if (scope == null || !scope.startsWith(LIFE_SCOPE_PREFIX)) return "unknown";
        String remainder = scope.substring(LIFE_SCOPE_PREFIX.length());
        int slash = remainder.indexOf('/');
        return slash > 0 ? remainder.substring(0, slash) : remainder;
    }

    private DomainSlaStats buildDomainSlaStats(String domain, List<WorkItem> items, Instant now) {
        long total = items.size();
        long breached = items.stream().filter(wi -> isSlaBreached(wi, now)).count();
        Double complianceRate = total > 0 ? (double) (total - breached) / total : null;

        List<Double> breachLatencies = items.stream()
                .filter(wi -> wi.completedAt != null && wi.expiresAt != null
                        && wi.completedAt.isAfter(wi.expiresAt))
                .map(wi -> Duration.between(wi.expiresAt, wi.completedAt).toMillis() / 3_600_000.0)
                .toList();

        Double avgBreachLatency = breachLatencies.isEmpty() ? null
                : breachLatencies.stream().mapToDouble(Double::doubleValue).average().orElse(0);

        return new DomainSlaStats(domain, total, breached, complianceRate, avgBreachLatency);
    }

    private boolean isSlaBreached(WorkItem wi, Instant now) {
        if (wi.status == WorkItemStatus.ESCALATED || wi.status == WorkItemStatus.EXPIRED) return true;
        if (wi.completedAt != null && wi.expiresAt != null && wi.completedAt.isAfter(wi.expiresAt)) return true;
        if (wi.expiresAt != null && wi.expiresAt.isBefore(now) && wi.status != null && wi.status.isActive() && wi.status != WorkItemStatus.SUSPENDED) return true;
        return false;
    }

    @Transactional
    public TrustAnalyticsResponse trustAnalytics() {
        List<ExternalActor> actors = ExternalActor.list("gdprErasedAt IS NULL");
        if (actors.isEmpty()) {
            return new TrustAnalyticsResponse(0, null, Map.of(), List.of());
        }

        List<String> actorIds = actors.stream()
                .map(a -> LifeActorIds.of(a.id))
                .toList();

        List<ActorTrustScore> globalScores = qhorusEm.createQuery(
                        "SELECT s FROM ActorTrustScore s WHERE s.actorId IN :actorIds AND s.scoreType = :scoreType AND s.capabilityKey IS NULL AND s.dimensionKey IS NULL",
                        ActorTrustScore.class)
                .setParameter("actorIds", actorIds)
                .setParameter("scoreType", ScoreType.GLOBAL)
                .getResultList();

        List<ActorTrustScore> dimensionScores = qhorusEm.createQuery(
                        "SELECT s FROM ActorTrustScore s WHERE s.actorId IN :actorIds AND s.scoreType = :scoreType AND s.dimensionKey IS NOT NULL",
                        ActorTrustScore.class)
                .setParameter("actorIds", actorIds)
                .setParameter("scoreType", ScoreType.GLOBAL)
                .getResultList();

        Map<String, UUID> actorIdToUuid = actors.stream()
                .collect(Collectors.toMap(a -> LifeActorIds.of(a.id), a -> a.id));
        Map<UUID, String> uuidToName = actors.stream()
                .collect(Collectors.toMap(a -> a.id, a -> a.name != null ? a.name : ""));

        Double avgGlobal = globalScores.isEmpty() ? null
                : globalScores.stream().mapToDouble(s -> s.trustScore).average().orElse(0);

        Map<String, Double> dimAverages = dimensionScores.stream()
                .collect(Collectors.groupingBy(
                        s -> s.dimensionKey,
                        Collectors.averagingDouble(s -> s.trustScore)));

        List<ActorScoreSummary> lowest = globalScores.stream()
                .sorted(Comparator.comparingDouble(s -> s.trustScore))
                .limit(5)
                .map(s -> {
                    UUID uuid = actorIdToUuid.get(s.actorId);
                    return new ActorScoreSummary(uuid, uuidToName.getOrDefault(uuid, ""), s.trustScore);
                })
                .toList();

        return new TrustAnalyticsResponse(globalScores.size(), avgGlobal, dimAverages, lowest);
    }
}
