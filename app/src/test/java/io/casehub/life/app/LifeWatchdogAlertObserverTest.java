package io.casehub.life.app;

import io.casehub.life.api.commitment.CommitmentMode;
import io.casehub.life.api.commitment.CommitmentStatus;
import io.casehub.life.app.entity.LifeCommitmentRecord;
import io.casehub.life.app.observer.LifeWatchdogAlertObserver;
import io.casehub.qhorus.api.watchdog.ApprovalPendingContext;
import io.casehub.qhorus.api.watchdog.ChannelIdleContext;
import io.casehub.qhorus.api.watchdog.WatchdogAlertEvent;
import io.casehub.work.runtime.model.WorkItem;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies LifeWatchdogAlertObserver.onAlert() end-to-end.
 *
 * onAlert() is called directly (not via evaluateAll()) to bypass the WatchdogEvaluationService
 * debounce check and the asynchronous CDI event dispatch. The @Transactional(REQUIRES_NEW)
 * interceptor on onAlert() is still honoured because observer is injected as a CDI proxy.
 *
 * Test methods are NOT @Transactional. onAlert() uses REQUIRES_NEW — an outer test transaction
 * would prevent the REQUIRES_NEW transaction from seeing the committed setup records.
 * See GE-20260529-9f3557 for the @TestTransaction + JPA auto-flush gotcha that motivated
 * direct observer invocation over evaluateAll() + Awaitility.
 *
 * Channel names must be globally unique within this test class to prevent cross-test
 * interference: all records accumulate in the same in-memory H2 DB across tests.
 */
@QuarkusTest
class LifeWatchdogAlertObserverTest {

    @Inject
    LifeWatchdogAlertObserver observer;

    @BeforeEach
    @Transactional
    void seedTemplates() {
        LifeTestFixtures.seedEscalationTemplate();
    }

    // --- Happy path: all three commitment modes ---

    @Test
    void onAlert_delegation_createsEscalationTaskWithDelegateTitleAndMarksExpired() {
        final String correlationId = insertRecord("life/del-obs-happy", CommitmentMode.DELEGATION,
                r -> r.delegateTo = "alice");
        final long workItemsBefore = WorkItem.count();

        observer.onAlert(approvalPendingEvent("life/del-obs-happy"));

        final LifeCommitmentRecord updated = LifeCommitmentRecord
                .findByCorrelationId(correlationId)
                .orElseThrow();
        assertThat(updated.status).isEqualTo(CommitmentStatus.EXPIRED);
        assertThat(WorkItem.count()).isEqualTo(workItemsBefore + 1);
        assertThat(latestWorkItemTitle()).isEqualTo("alice has not confirmed — action required");
    }

    @Test
    void onAlert_contractor_createsEscalationTaskAndMarksExpired() {
        final String correlationId = insertRecord("life/del-obs-contractor", CommitmentMode.CONTRACTOR,
                r -> r.externalActorId = UUID.randomUUID());
        final long workItemsBefore = WorkItem.count();

        observer.onAlert(approvalPendingEvent("life/del-obs-contractor"));

        final LifeCommitmentRecord updated = LifeCommitmentRecord
                .findByCorrelationId(correlationId)
                .orElseThrow();
        assertThat(updated.status).isEqualTo(CommitmentStatus.EXPIRED);
        assertThat(WorkItem.count()).isEqualTo(workItemsBefore + 1);
        assertThat(latestWorkItemTitle()).isEqualTo("Contractor has not confirmed by deadline");
    }

    @Test
    void onAlert_oversight_createsEscalationTaskAndMarksExpired() {
        final String correlationId = insertRecord("life/del-obs-oversight", CommitmentMode.OVERSIGHT,
                r -> {
                    r.delegateTo = "Buy new car:household-task";
                    r.amountThreshold = java.math.BigDecimal.valueOf(5000);
                    r.purchaseCategory = "vehicle";
                });
        final long workItemsBefore = WorkItem.count();

        observer.onAlert(approvalPendingEvent("life/del-obs-oversight"));

        final LifeCommitmentRecord updated = LifeCommitmentRecord
                .findByCorrelationId(correlationId)
                .orElseThrow();
        assertThat(updated.status).isEqualTo(CommitmentStatus.EXPIRED);
        assertThat(WorkItem.count()).isEqualTo(workItemsBefore + 1);
        assertThat(latestWorkItemTitle()).isEqualTo("Oversight gate expired — request not approved");
    }

    // --- Title branch coverage ---

    @Test
    void onAlert_delegation_colonPrefix_producesOversightExpiredTitle() {
        final String correlationId = insertRecord("life/del-obs-colon", CommitmentMode.DELEGATION,
                r -> r.delegateTo = "life:system-key");
        final long workItemsBefore = WorkItem.count();

        observer.onAlert(approvalPendingEvent("life/del-obs-colon"));

        assertThat(WorkItem.count()).isEqualTo(workItemsBefore + 1);
        assertThat(latestWorkItemTitle()).isEqualTo("Oversight gate expired — request not approved");
    }

    @Test
    void onAlert_delegation_nullDelegateTo_createsEscalationWithFallbackTitle() {
        final String correlationId = insertRecord("life/del-obs-null-delegate", CommitmentMode.DELEGATION,
                r -> r.delegateTo = null);
        final long workItemsBefore = WorkItem.count();

        observer.onAlert(approvalPendingEvent("life/del-obs-null-delegate"));

        final LifeCommitmentRecord updated = LifeCommitmentRecord
                .findByCorrelationId(correlationId)
                .orElseThrow();
        assertThat(updated.status).isEqualTo(CommitmentStatus.EXPIRED);
        assertThat(WorkItem.count()).isEqualTo(workItemsBefore + 1);
        assertThat(latestWorkItemTitle()).isEqualTo("Unknown has not confirmed — action required");
    }

    // --- Guard clause: non-APPROVAL_PENDING condition type is a no-op ---

    @Test
    void onAlert_nonApprovalPendingCondition_doesNotProcessCommitments() {
        final String correlationId = insertRecord("life/del-obs-idle", CommitmentMode.DELEGATION,
                r -> r.delegateTo = "bob");
        final long workItemsBefore = WorkItem.count();

        observer.onAlert(new WatchdogAlertEvent(
                UUID.randomUUID(), "life-watchdog", "life/del-obs-idle",
                "Channel idle", Instant.now(),
                new ChannelIdleContext(List.of("life/del-obs-idle"), 300L)
        ));

        final LifeCommitmentRecord unchanged = LifeCommitmentRecord
                .findByCorrelationId(correlationId)
                .orElseThrow();
        assertThat(unchanged.status).isEqualTo(CommitmentStatus.PENDING_RESPONSE);
        assertThat(WorkItem.count()).isEqualTo(workItemsBefore);
    }

    // --- Robustness: future deadline records are not processed ---

    @Test
    void onAlert_futureDeadlineCommitment_notProcessed() {
        final String correlationId = insertRecord("life/del-obs-future", CommitmentMode.DELEGATION,
                Instant.now().plus(1, ChronoUnit.HOURS),
                r -> r.delegateTo = "carol");

        observer.onAlert(approvalPendingEvent("life/del-obs-future"));

        final LifeCommitmentRecord unchanged = LifeCommitmentRecord
                .findByCorrelationId(correlationId)
                .orElseThrow();
        assertThat(unchanged.status).isEqualTo(CommitmentStatus.PENDING_RESPONSE);
    }

    // --- Helpers ---

    @FunctionalInterface
    interface RecordCustomizer {
        void customize(LifeCommitmentRecord record);
    }

    @Transactional
    String insertRecord(final String channelId, final CommitmentMode mode,
                        final RecordCustomizer customizer) {
        return insertRecord(channelId, mode,
                Instant.now().minus(1, ChronoUnit.SECONDS), customizer);
    }

    @Transactional
    String insertRecord(final String channelId, final CommitmentMode mode,
                        final Instant deadline, final RecordCustomizer customizer) {
        final String correlationId = UUID.randomUUID().toString();
        final LifeCommitmentRecord record = new LifeCommitmentRecord();
        record.id = UUID.randomUUID();
        record.correlationId = correlationId;
        record.mode = mode;
        record.status = CommitmentStatus.PENDING_RESPONSE;
        record.channelId = channelId;
        record.deadline = deadline;
        record.createdAt = record.updatedAt = Instant.now();
        customizer.customize(record);
        record.persist();
        return correlationId;
    }

    private WatchdogAlertEvent approvalPendingEvent(final String channel) {
        return new WatchdogAlertEvent(
                UUID.randomUUID(), "life-watchdog", channel,
                "Approval pending on " + channel, Instant.now(),
                new ApprovalPendingContext(1, Instant.now().minus(1, ChronoUnit.SECONDS))
        );
    }

    private String latestWorkItemTitle() {
        return WorkItem.<WorkItem>listAll().stream()
                .max(java.util.Comparator.comparing(w -> w.createdAt))
                .map(w -> w.title)
                .orElse(null);
    }
}
