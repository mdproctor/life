package io.casehub.life.app.service;

import io.casehub.life.api.LifeCaseStatus;
import io.casehub.life.api.LifeCaseType;
import io.casehub.life.api.LifeDomain;
import io.casehub.life.api.response.LifeCaseDetailResponse;
import io.casehub.life.api.response.LifeCaseResponse;
import io.casehub.life.api.response.PagedResponse;
import io.casehub.life.api.spi.LifeCaseVisibilityPolicy;
import io.casehub.life.app.entity.LifeCaseTracker;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class LifeCaseQueryService {

    @Inject CurrentPrincipal currentPrincipal;
    @Inject LifeCaseVisibilityPolicy visibilityPolicy;

    @Transactional
    public PagedResponse<LifeCaseResponse> listCases(LifeDomain domain,
                                                      LifeCaseStatus status,
                                                      LifeCaseType caseType,
                                                      int page, int size) {
        QueryParts query = buildListQuery(domain, status, caseType);
        long total = LifeCaseTracker.count(query.hql(), query.params());
        List<LifeCaseTracker> trackers = LifeCaseTracker.find(query.hql(),
                        Sort.by("createdAt", Sort.Direction.Descending), query.params())
                .page(Page.of(page, size))
                .list();

        String actorId = currentPrincipal.actorId();
        Set<String> groups = currentPrincipal.groups();

        List<LifeCaseResponse> items = trackers.stream()
                .map(this::toResponse)
                .filter(r -> visibilityPolicy.isVisible(r, actorId, groups))
                .toList();

        return new PagedResponse<>(items, page, size, total);
    }

    @Transactional
    public Optional<LifeCaseDetailResponse> findById(UUID id) {
        LifeCaseTracker tracker = LifeCaseTracker.findById(id);
        if (tracker == null) return Optional.empty();

        LifeCaseResponse response = toResponse(tracker);
        String actorId = currentPrincipal.actorId();
        Set<String> groups = currentPrincipal.groups();

        if (!visibilityPolicy.isVisible(response, actorId, groups)) {
            return Optional.empty();
        }

        return Optional.of(toDetailResponse(tracker));
    }

    private LifeCaseResponse toResponse(LifeCaseTracker tracker) {
        return new LifeCaseResponse(
                tracker.id,
                LifeCaseType.valueOf(caseNameToEnumName(tracker.caseType)),
                tracker.domain,
                tracker.status,
                tracker.createdAt,
                tracker.completedAt
        );
    }

    private LifeCaseDetailResponse toDetailResponse(LifeCaseTracker tracker) {
        return new LifeCaseDetailResponse(
                tracker.id,
                LifeCaseType.valueOf(caseNameToEnumName(tracker.caseType)),
                tracker.domain,
                tracker.status,
                tracker.createdAt,
                tracker.completedAt,
                tracker.engineCaseId
        );
    }

    private String caseNameToEnumName(String caseName) {
        return caseName.toUpperCase().replace('-', '_');
    }

    private record QueryParts(String hql, Map<String, Object> params) {}

    private QueryParts buildListQuery(LifeDomain domain, LifeCaseStatus status,
                                       LifeCaseType caseType) {
        var conditions = new ArrayList<String>();
        var params = new HashMap<String, Object>();

        if (domain != null) {
            conditions.add("domain = :domain");
            params.put("domain", domain);
        }
        if (status != null) {
            conditions.add("status = :status");
            params.put("status", status);
        }
        if (caseType != null) {
            conditions.add("caseType = :caseType");
            params.put("caseType", caseType.caseName());
        }

        String hql = conditions.isEmpty() ? "" : String.join(" and ", conditions);
        return new QueryParts(hql, params);
    }
}
