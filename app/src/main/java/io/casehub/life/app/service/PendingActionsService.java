package io.casehub.life.app.service;

import io.casehub.life.api.LifeDomain;
import io.casehub.life.api.Urgency;
import io.casehub.life.api.response.PagedResponse;
import io.casehub.life.api.response.PendingActionResponse;
import io.casehub.life.app.entity.LifeTaskContext;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.runtime.model.WorkItem;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@ApplicationScoped
public class PendingActionsService {

    private static final String LIFE_SCOPE_PREFIX = "casehubio/life/";
    private static final List<WorkItemStatus> ACTIONABLE_STATUSES = List.of(
            WorkItemStatus.PENDING, WorkItemStatus.ASSIGNED,
            WorkItemStatus.IN_PROGRESS, WorkItemStatus.DELEGATED);

    @Transactional
    public PagedResponse<PendingActionResponse> findPendingActions(
            LifeDomain domain, String candidateGroup, int dueSoonHours, int page, int size) {
        page = Math.max(page, 0);
        size = Math.max(Math.min(size, 100), 1);

        var params = new HashMap<String, Object>();
        var conditions = new ArrayList<String>();
        conditions.add("scope LIKE :scopePrefix");
        params.put("scopePrefix", LIFE_SCOPE_PREFIX + "%");
        conditions.add("status IN :statuses");
        params.put("statuses", ACTIONABLE_STATUSES);

        if (domain != null) {
            conditions.add("scope LIKE :domainScope");
            params.put("domainScope", LIFE_SCOPE_PREFIX + domain.descriptor().templateCategory() + "%");
        }
        if (candidateGroup != null && !candidateGroup.isBlank()) {
            conditions.add("candidateGroups LIKE :candidateGroup");
            params.put("candidateGroup", "%" + candidateGroup + "%");
        }

        String whereClause = String.join(" AND ", conditions);
        long total = WorkItem.count(whereClause, params);

        Instant now = Instant.now();
        var orderParams = new HashMap<>(params);
        orderParams.put("now", now);
        orderParams.put("dueSoonCutoff", now.plus(dueSoonHours, ChronoUnit.HOURS));

        String query = whereClause
                + " ORDER BY CASE WHEN expiresAt IS NULL THEN 3 WHEN expiresAt <= :now THEN 0"
                + " WHEN expiresAt <= :dueSoonCutoff THEN 1 ELSE 2 END ASC,"
                + " expiresAt ASC NULLS LAST, createdAt ASC NULLS LAST";

        List<WorkItem> items = WorkItem.<WorkItem>find(query, orderParams)
                .page(Page.of(page, size)).list();

        List<PendingActionResponse> responses = items.stream()
                .map(wi -> toPendingAction(wi, now, dueSoonHours))
                .toList();

        return new PagedResponse<>(responses, page, size, total);
    }

    private PendingActionResponse toPendingAction(WorkItem wi, Instant now, int dueSoonHours) {
        LifeDomain domain = resolveDomain(wi);
        Urgency urgency = Urgency.classify(wi.expiresAt, now, dueSoonHours);
        Long daysOverdue = Urgency.daysOverdue(wi.expiresAt, now);

        return new PendingActionResponse(
                wi.id, wi.title, wi.description,
                wi.status != null ? wi.status.name() : null,
                domain, wi.candidateGroups,
                wi.createdAt, wi.expiresAt, urgency, daysOverdue);
    }

    private LifeDomain resolveDomain(WorkItem wi) {
        return LifeTaskContext.<LifeTaskContext>findByIdOptional(wi.id)
                .map(ctx -> ctx.domain)
                .orElseGet(() -> domainFromScope(wi.scope));
    }

    private LifeDomain domainFromScope(String scope) {
        if (scope == null || !scope.startsWith(LIFE_SCOPE_PREFIX)) return null;
        String segment = scope.substring(LIFE_SCOPE_PREFIX.length());
        int slash = segment.indexOf('/');
        if (slash > 0) segment = segment.substring(0, slash);
        return LifeDomain.fromCategory(segment).orElse(null);
    }

}
