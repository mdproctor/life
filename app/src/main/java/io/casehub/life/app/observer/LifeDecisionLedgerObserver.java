package io.casehub.life.app.observer;

import io.casehub.life.api.LifeDomain;
import io.casehub.life.app.LifeDecisionEventType;
import io.casehub.life.app.entity.LifeTaskContext;
import io.casehub.life.app.service.ledger.DomainLedgerHandler;
import io.casehub.work.runtime.event.SlaBreachEvent;
import io.casehub.work.runtime.event.WorkItemLifecycleEvent;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.UUID;

@ApplicationScoped
public class LifeDecisionLedgerObserver {

    @Inject @Any
    Instance<DomainLedgerHandler> handlers;

    static LifeDomain domainFromScope(String scope) {
        if (scope == null || scope.isEmpty()) return null;
        String[] segments = scope.split("/");
        if (segments.length < 3) return null;
        try {
            return LifeDomain.valueOf(segments[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private LifeDomain resolveDomain(UUID workItemId, WorkItem workItem) {
        LifeDomain domain = domainFromScope(workItem.scope);
        if (domain != null) return domain;
        return LifeTaskContext.<LifeTaskContext>findByIdOptional(workItemId)
                .map(ctx -> ctx.domain)
                .orElse(null);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onSlaBreachEvent(@Observes final SlaBreachEvent event) {
        resolveAndWrite(event.context().task().taskId(), LifeDecisionEventType.SLA_BREACH);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onLifecycleEvent(@Observes final WorkItemLifecycleEvent event) {
        if (event.status() != WorkItemStatus.COMPLETED) return;
        resolveAndWrite(event.workItemId(), LifeDecisionEventType.COMPLETED);
    }

    private void resolveAndWrite(UUID workItemId, LifeDecisionEventType eventType) {
        WorkItem workItem = WorkItem.<WorkItem>findByIdOptional(workItemId).orElse(null);
        if (workItem == null) return;
        LifeDomain domain = resolveDomain(workItemId, workItem);
        if (domain == null) return; // unresolvable domain — deliberate no-op
        handlers.stream()
                .filter(h -> h.domain() == domain)
                .findFirst()
                .ifPresent(h -> h.writeEntry(eventType, workItemId, workItem));
    }
}
