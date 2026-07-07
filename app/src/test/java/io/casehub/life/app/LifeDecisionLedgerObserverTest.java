package io.casehub.life.app;

import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.life.api.LifeDomain;
import io.casehub.life.app.entity.LifeTaskContext;
import io.casehub.life.app.ledger.HealthDecisionLedgerEntry;
import io.casehub.life.app.ledger.LegalActionLedgerEntry;
import io.casehub.life.app.observer.LifeDecisionLedgerObserver;
import io.casehub.work.api.BreachDecision;
import io.casehub.work.api.BreachType;
import io.casehub.work.api.BreachedTask;
import io.casehub.work.api.SlaBreachContext;
import io.casehub.work.runtime.event.SlaBreachEvent;
import io.casehub.work.runtime.event.WorkItemLifecycleEvent;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.runtime.service.WorkItemService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class LifeDecisionLedgerObserverTest {

    @Inject LifeDecisionLedgerObserver observer;
    @Inject LedgerEntryRepository ledgerRepository;
    @Inject WorkItemService workItemService;

    private UUID healthWorkItemId;
    private UUID legalWorkItemId;
    private UUID householdWorkItemId;

    @BeforeEach
    @Transactional
    void setUp() {
        LifeTestFixtures.seedStandardTemplates();
        healthWorkItemId = createWorkItemWithContext("health", LifeDomain.HEALTH);
        legalWorkItemId = createWorkItemWithContext("legal", LifeDomain.LEGAL);
        householdWorkItemId = createWorkItemWithContext("household", LifeDomain.HOUSEHOLD);
    }

    @Test
    void onSlaBreachEvent_writesHealthSlaBreachEntry() {
        observer.onSlaBreachEvent(breachEvent(healthWorkItemId));

        var entry = ledgerRepository.findLatestBySubjectId(healthWorkItemId, TenancyConstants.DEFAULT_TENANT_ID).orElseThrow();
        assertThat(entry).isInstanceOf(HealthDecisionLedgerEntry.class);
        assertThat(((HealthDecisionLedgerEntry) entry).eventType).isEqualTo(LifeDecisionEventType.SLA_BREACH);
    }

    @Test
    void onSlaBreachEvent_writesLegalSlaBreachEntry() {
        observer.onSlaBreachEvent(breachEvent(legalWorkItemId));

        var entry = ledgerRepository.findLatestBySubjectId(legalWorkItemId, TenancyConstants.DEFAULT_TENANT_ID).orElseThrow();
        assertThat(entry).isInstanceOf(LegalActionLedgerEntry.class);
        assertThat(((LegalActionLedgerEntry) entry).eventType).isEqualTo(LifeDecisionEventType.SLA_BREACH);
    }

    @Test
    void onSlaBreachEvent_skipsHouseholdDomainTask() {
        observer.onSlaBreachEvent(breachEvent(householdWorkItemId));
        assertThat(ledgerRepository.findLatestBySubjectId(householdWorkItemId, TenancyConstants.DEFAULT_TENANT_ID)).isEmpty();
    }

    @Test
    void onSlaBreachEvent_skipsWorkItemWithNoLifeTaskContext() {
        var orphanId = createBareWorkItem();
        observer.onSlaBreachEvent(breachEvent(orphanId));
        assertThat(ledgerRepository.findLatestBySubjectId(orphanId, TenancyConstants.DEFAULT_TENANT_ID)).isEmpty();
    }

    @Test
    void onLifecycleEvent_writesHealthCompletedEntry() {
        observer.onLifecycleEvent(completedEvent(healthWorkItemId, "appointment-confirmed"));

        var entry = (HealthDecisionLedgerEntry) ledgerRepository
                .findLatestBySubjectId(healthWorkItemId, TenancyConstants.DEFAULT_TENANT_ID).orElseThrow();
        assertThat(entry.eventType).isEqualTo(LifeDecisionEventType.COMPLETED);
        assertThat(entry.outcome).isEqualTo("appointment-confirmed");
    }

    @Test
    void onLifecycleEvent_writesLegalCompletedEntry() {
        observer.onLifecycleEvent(completedEvent(legalWorkItemId, "filed-online"));

        var entry = (LegalActionLedgerEntry) ledgerRepository
                .findLatestBySubjectId(legalWorkItemId, TenancyConstants.DEFAULT_TENANT_ID).orElseThrow();
        assertThat(entry.eventType).isEqualTo(LifeDecisionEventType.COMPLETED);
        assertThat(entry.actionTaken).isEqualTo("filed-online");
    }

    @Test
    void onLifecycleEvent_skipsRejectedStatus() {
        var wi = loadWorkItem(healthWorkItemId);
        var event = WorkItemLifecycleEvent.of("REJECTED", wi, "life-system", null);
        observer.onLifecycleEvent(event);
        assertThat(ledgerRepository.findLatestBySubjectId(healthWorkItemId, TenancyConstants.DEFAULT_TENANT_ID)).isEmpty();
    }

    @Test
    void onLifecycleEvent_skipsHouseholdDomain() {
        observer.onLifecycleEvent(completedEvent(householdWorkItemId, "done"));
        assertThat(ledgerRepository.findLatestBySubjectId(householdWorkItemId, TenancyConstants.DEFAULT_TENANT_ID)).isEmpty();
    }

    @Test
    void onLifecycleEvent_skipsWorkItemWithNoLifeTaskContext() {
        var orphanId = createBareWorkItem();
        observer.onLifecycleEvent(completedEvent(orphanId, "done"));
        assertThat(ledgerRepository.findLatestBySubjectId(orphanId, TenancyConstants.DEFAULT_TENANT_ID)).isEmpty();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    @Transactional
    UUID createWorkItemWithContext(String category, LifeDomain domain) {
        var req = WorkItemCreateRequest.builder()
                .title("Test " + category + " task")
                .types(java.util.List.of(category))
                .priority(WorkItemPriority.MEDIUM)
                .candidateGroups("household-member")
                .createdBy("life-system")
                .callerRef("life:task/test")
                .scope("casehubio/life/" + domain.name().toLowerCase())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        var workItem = workItemService.create(req);

        var ctx = new LifeTaskContext();
        ctx.workItemId = workItem.id;
        ctx.domain = domain;
        ctx.persist();

        return workItem.id;
    }

    @Transactional
    UUID createBareWorkItem() {
        var req = WorkItemCreateRequest.builder()
                .title("Bare task — no LifeTaskContext")
                .types(java.util.List.of("other"))
                .priority(WorkItemPriority.MEDIUM)
                .candidateGroups("household-member")
                .createdBy("life-system")
                .callerRef("life:task/test")
                .scope("casehubio/life/household")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        return workItemService.create(req).id;
    }

    @Transactional
    WorkItem completeWorkItem(UUID id, String outcome) {
        var wi = WorkItem.<WorkItem>findByIdOptional(id).orElseThrow();
        wi.outcome = outcome;
        wi.status = WorkItemStatus.COMPLETED;
        wi.persist();
        return wi;
    }

    @Transactional
    WorkItem loadWorkItem(UUID id) {
        return WorkItem.<WorkItem>findByIdOptional(id).orElseThrow();
    }

    private SlaBreachEvent breachEvent(UUID taskId) {
        var breachedTask = new BreachedTask(taskId, null, "test task", Set.of("household-member"));
        var ctx = new SlaBreachContext(BreachType.COMPLETION_EXPIRED, breachedTask,
                io.casehub.platform.api.path.Path.root(),
                null);
        return new SlaBreachEvent(ctx, new BreachDecision.Fail("deadline"), TenancyConstants.DEFAULT_TENANT_ID);
    }

    private WorkItemLifecycleEvent completedEvent(UUID workItemId, String outcome) {
        var wi = completeWorkItem(workItemId, outcome);
        return WorkItemLifecycleEvent.of("COMPLETED", wi, "life-system", null);
    }
}
