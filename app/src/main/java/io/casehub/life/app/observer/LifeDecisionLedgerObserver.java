package io.casehub.life.app.observer;

import io.casehub.life.api.LifeDomain;
import io.casehub.life.app.LifeDecisionEventType;
import io.casehub.life.app.entity.LifeCommitmentRecord;
import io.casehub.life.app.entity.LifeTaskContext;
import io.casehub.life.app.service.ledger.LifeLedgerWriter;
import io.casehub.work.runtime.event.SlaBreachEvent;
import io.casehub.work.runtime.event.WorkItemLifecycleEvent;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class LifeDecisionLedgerObserver {

    @Inject LifeLedgerWriter lifeLedgerWriter;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onSlaBreachEvent(@Observes final SlaBreachEvent event) {
        final var taskId = event.context().task().taskId();
        final var ctx = LifeTaskContext.<LifeTaskContext>findByIdOptional(taskId).orElse(null);
        if (ctx == null) return;

        final var workItem = WorkItem.<WorkItem>findByIdOptional(taskId).orElse(null);
        if (workItem == null) return;

        switch (ctx.domain) {
            case HEALTH -> lifeLedgerWriter.writeHealthEntry(LifeDecisionEventType.SLA_BREACH, ctx, workItem);
            case LEGAL -> lifeLedgerWriter.writeLegalEntry(LifeDecisionEventType.SLA_BREACH, ctx, workItem);
            case FINANCE -> LifeCommitmentRecord.findByWorkItemId(taskId).ifPresent(record ->
                    lifeLedgerWriter.writeFinancialEntry(LifeDecisionEventType.SLA_BREACH, record, taskId));
            default -> { }
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onLifecycleEvent(@Observes final WorkItemLifecycleEvent event) {
        if (event.status() != WorkItemStatus.COMPLETED) return;

        final var taskId = event.workItemId();
        final var ctx = LifeTaskContext.<LifeTaskContext>findByIdOptional(taskId).orElse(null);
        if (ctx == null) return;

        final var workItem = WorkItem.<WorkItem>findByIdOptional(taskId).orElse(null);
        if (workItem == null) return;

        switch (ctx.domain) {
            case HEALTH -> lifeLedgerWriter.writeHealthEntry(LifeDecisionEventType.COMPLETED, ctx, workItem);
            case LEGAL -> lifeLedgerWriter.writeLegalEntry(LifeDecisionEventType.COMPLETED, ctx, workItem);
            case FINANCE -> LifeCommitmentRecord.findByWorkItemId(taskId).ifPresent(record ->
                    lifeLedgerWriter.writeFinancialEntry(LifeDecisionEventType.COMPLETED, record, taskId));
            default -> { }
        }
    }
}
