package io.casehub.life.app.observer;

import io.casehub.life.api.commitment.CommitmentStatus;
import io.casehub.life.api.request.CreateLifeTaskRequest;
import io.casehub.life.app.entity.LifeCommitmentRecord;
import io.casehub.life.app.service.LifeTaskService;
import io.casehub.qhorus.api.watchdog.WatchdogAlertEvent;
import io.casehub.qhorus.api.watchdog.WatchdogConditionType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Creates escalation WorkItems when an APPROVAL_PENDING Watchdog fires.
 *
 * WatchdogAlertEvent carries notificationChannel (channel name), not correlationId.
 * The evaluator fires one event per Watchdog condition (per channel), not per Commitment.
 * This observer queries all expired PENDING_RESPONSE records on that channel.
 *
 * @ObservesAsync: qhorus WatchdogEvaluationService fires WatchdogAlertEvent via fireAsync().
 * Only @ObservesAsync observers receive async events — @Observes would not fire here.
 * @Transactional(REQUIRES_NEW): the observer runs in its own transaction independent of
 * the qhorus evaluation cycle transaction. See life#17 for integration test gap.
 */
@ApplicationScoped
public class LifeWatchdogAlertObserver {

    private static final Logger LOG = Logger.getLogger(LifeWatchdogAlertObserver.class);

    @Inject
    LifeTaskService lifeTaskService;

    @Inject
    ObjectMapper objectMapper;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onAlert(@ObservesAsync final WatchdogAlertEvent event) {
        if (event.conditionType() != WatchdogConditionType.APPROVAL_PENDING) return;

        final List<LifeCommitmentRecord> expired = LifeCommitmentRecord
                .findExpiredPendingByChannel(event.notificationChannel(), Instant.now());

        for (final LifeCommitmentRecord record : expired) {
            createEscalationTask(record);
            record.status = CommitmentStatus.EXPIRED;
            record.updatedAt = Instant.now();
            record.persist();
        }
    }

    private void createEscalationTask(final LifeCommitmentRecord record) {
        final String title = switch (record.mode) {
            case DELEGATION -> {
                final String delegate = record.delegateTo;
                yield delegate != null && delegate.contains(":")
                        ? "Oversight gate expired — request not approved"
                        : (delegate != null ? delegate : "Unknown")
                                + " has not confirmed — action required";
            }
            case CONTRACTOR -> "Contractor has not confirmed by deadline";
            case OVERSIGHT  -> "Oversight gate expired — request not approved";
        };

        // "life-escalation" WorkItemTemplate seeded at V104.
        // CreateLifeTaskRequest(templateRef, title, externalActorId, deadline)
        lifeTaskService.create(new CreateLifeTaskRequest("life-escalation", title, null, null));
    }
}
