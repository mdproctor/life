package io.casehub.life.app.service.ledger;

import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.life.api.LifeDomain;
import io.casehub.life.app.LifeDecisionEventType;
import io.casehub.life.app.entity.ExternalActor;
import io.casehub.life.app.entity.LifeCommitmentRecord;
import io.casehub.life.app.entity.LifeTaskContext;
import io.casehub.life.app.ledger.ExternalActorErasureLedgerEntry;
import io.casehub.life.app.ledger.FinancialDecisionLedgerEntry;
import io.casehub.life.app.ledger.HealthDecisionLedgerEntry;
import io.casehub.life.app.ledger.LegalActionLedgerEntry;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.work.runtime.model.WorkItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LifeLedgerWriterTest {

    @Mock LedgerEntryRepository ledgerRepository;
    @Mock LifeOutcomeAttestationWriter attestationWriter;
    @InjectMocks LifeLedgerWriter writer;

    private static final UUID WORK_ITEM_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final UUID OVERSIGHT_ID = UUID.randomUUID();
    private static final Instant SLA = Instant.parse("2026-06-01T09:00:00Z");

    @BeforeEach
    void setUp() {
        when(ledgerRepository.findLatestBySubjectId(any())).thenReturn(Optional.empty());
        when(ledgerRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    // ── Health ─────────────────────────────────────────────────────────────

    @Test
    void writeHealthEntry_created_setsRequiredFields() {
        writer.writeHealthEntry(LifeDecisionEventType.CREATED, healthCtx(null), workItem("health", SLA));

        var entry = captureHealth();
        assertThat(entry.workItemId).isEqualTo(WORK_ITEM_ID);
        assertThat(entry.taskCategory).isEqualTo("health");
        assertThat(entry.slaDeadline).isEqualTo(SLA);
        assertThat(entry.eventType).isEqualTo(LifeDecisionEventType.CREATED);
        assertThat(entry.outcome).isNull();
        assertThat(entry.actorId).isEqualTo("life-system");
        assertThat(entry.actorType).isEqualTo(ActorType.SYSTEM);
        assertThat(entry.actorRole).isEqualTo("HealthDecisionAudit");
        assertThat(entry.entryType).isEqualTo(LedgerEntryType.EVENT);
        assertThat(entry.sequenceNumber).isEqualTo(1);
    }

    @Test
    void writeHealthEntry_sequenceNumberIncrementsFromPrior() {
        var prior = new HealthDecisionLedgerEntry();
        prior.sequenceNumber = 3;
        when(ledgerRepository.findLatestBySubjectId(WORK_ITEM_ID)).thenReturn(Optional.of(prior));

        writer.writeHealthEntry(LifeDecisionEventType.SLA_BREACH, healthCtx(null), workItem("health", SLA));

        assertThat(captureHealth().sequenceNumber).isEqualTo(4);
    }

    @Test
    void writeHealthEntry_slaBreach_eventType() {
        writer.writeHealthEntry(LifeDecisionEventType.SLA_BREACH, healthCtx(null), workItem("health", SLA));
        assertThat(captureHealth().eventType).isEqualTo(LifeDecisionEventType.SLA_BREACH);
    }

    @Test
    void writeHealthEntry_completed_setsOutcome() {
        var wi = workItem("health", SLA);
        wi.outcome = "appointment-confirmed";

        writer.writeHealthEntry(LifeDecisionEventType.COMPLETED, healthCtx(null), wi);

        assertThat(captureHealth().outcome).isEqualTo("appointment-confirmed");
    }

    @Test
    void writeHealthEntry_nullProviderIdWhenNoActor() {
        writer.writeHealthEntry(LifeDecisionEventType.CREATED, healthCtx(null), workItem("health", SLA));
        assertThat(captureHealth().providerId).isNull();
    }

    @Test
    void writeHealthEntry_setsProviderIdWhenActorPresent() {
        writer.writeHealthEntry(LifeDecisionEventType.CREATED, healthCtx(ACTOR_ID), workItem("health", SLA));
        assertThat(captureHealth().providerId).isEqualTo(ACTOR_ID);
    }

    // ── Financial ──────────────────────────────────────────────────────────

    @Test
    void writeFinancialEntry_created_setsOversightRefAndAmount() {
        writer.writeFinancialEntry(LifeDecisionEventType.CREATED, oversightRecord(), null);

        var entry = captureFinancial();
        assertThat(entry.oversightRef).isEqualTo(OVERSIGHT_ID);
        assertThat(entry.amountThreshold).isEqualByComparingTo("1500.00");
        assertThat(entry.purchaseCategory).isEqualTo("appliance");
        assertThat(entry.workItemId).isNull();
        assertThat(entry.approvedBy).isNull();
        assertThat(entry.eventType).isEqualTo(LifeDecisionEventType.CREATED);
        assertThat(entry.subjectId).isEqualTo(OVERSIGHT_ID);
    }

    @Test
    void writeFinancialEntry_completed_setsApprovedByAndWorkItemId() {
        var record = oversightRecord();
        record.approvedBy = "household-admin";

        writer.writeFinancialEntry(LifeDecisionEventType.COMPLETED, record, WORK_ITEM_ID);

        var entry = captureFinancial();
        assertThat(entry.approvedBy).isEqualTo("household-admin");
        assertThat(entry.workItemId).isEqualTo(WORK_ITEM_ID);
    }

    @Test
    void writeFinancialEntry_slaBreach_nullWorkItemId() {
        writer.writeFinancialEntry(LifeDecisionEventType.SLA_BREACH, oversightRecord(), null);
        assertThat(captureFinancial().workItemId).isNull();
    }

    // ── Legal ──────────────────────────────────────────────────────────────

    @Test
    void writeLegalEntry_created_setsLegalObligationAndDeadline() {
        var wi = workItem("legal", SLA);
        wi.title = "File Tax Return 2026";

        writer.writeLegalEntry(LifeDecisionEventType.CREATED, legalCtx(), wi);

        var entry = captureLegal();
        assertThat(entry.workItemId).isEqualTo(WORK_ITEM_ID);
        assertThat(entry.legalObligation).isEqualTo("File Tax Return 2026");
        assertThat(entry.filingDeadline).isEqualTo(SLA);
        assertThat(entry.eventType).isEqualTo(LifeDecisionEventType.CREATED);
        assertThat(entry.actionTaken).isNull();
    }

    @Test
    void writeLegalEntry_completed_setsActionTaken() {
        var wi = workItem("legal", SLA);
        wi.title = "Annual Filing";
        wi.outcome = "filed-online";

        writer.writeLegalEntry(LifeDecisionEventType.COMPLETED, legalCtx(), wi);

        assertThat(captureLegal().actionTaken).isEqualTo("filed-online");
    }

    // ── Erasure ────────────────────────────────────────────────────────────

    @Test
    void writeErasureEntry_setsRequiredFields() {
        writer.writeErasureEntry(externalActor(), "household-admin");

        var entry = captureErasure();
        assertThat(entry.erasedActorId).isEqualTo(ACTOR_ID);
        assertThat(entry.contactMethod).isEqualTo("phone");
        assertThat(entry.erasedBy).isEqualTo("household-admin");
        assertThat(entry.subjectId).isEqualTo(ACTOR_ID);
        assertThat(entry.actorId).isEqualTo("household-admin");
        assertThat(entry.actorType).isEqualTo(ActorType.HUMAN);
        assertThat(entry.actorRole).isEqualTo("GdprDataController");
        assertThat(entry.sequenceNumber).isEqualTo(1);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private LifeTaskContext healthCtx(UUID providerId) {
        var ctx = new LifeTaskContext();
        ctx.workItemId = WORK_ITEM_ID;
        ctx.domain = LifeDomain.HEALTH;
        ctx.externalActorId = providerId;
        return ctx;
    }

    private LifeTaskContext legalCtx() {
        var ctx = new LifeTaskContext();
        ctx.workItemId = WORK_ITEM_ID;
        ctx.domain = LifeDomain.LEGAL;
        return ctx;
    }

    private WorkItem workItem(String category, Instant expiresAt) {
        var wi = new WorkItem();
        wi.id = WORK_ITEM_ID;
        wi.category = category;
        wi.expiresAt = expiresAt;
        return wi;
    }

    private LifeCommitmentRecord oversightRecord() {
        var r = new LifeCommitmentRecord();
        r.id = OVERSIGHT_ID;
        r.amountThreshold = new BigDecimal("1500.00");
        r.purchaseCategory = "appliance";
        return r;
    }

    private ExternalActor externalActor() {
        var a = new ExternalActor();
        a.id = ACTOR_ID;
        a.contactMethod = "phone";
        a.name = "Bob";
        a.contactValue = "+44-7700-900001";
        return a;
    }

    private HealthDecisionLedgerEntry captureHealth() {
        var cap = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerRepository).save(cap.capture());
        return (HealthDecisionLedgerEntry) cap.getValue();
    }

    private FinancialDecisionLedgerEntry captureFinancial() {
        var cap = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerRepository).save(cap.capture());
        return (FinancialDecisionLedgerEntry) cap.getValue();
    }

    private LegalActionLedgerEntry captureLegal() {
        var cap = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerRepository).save(cap.capture());
        return (LegalActionLedgerEntry) cap.getValue();
    }

    private ExternalActorErasureLedgerEntry captureErasure() {
        var cap = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerRepository).save(cap.capture());
        return (ExternalActorErasureLedgerEntry) cap.getValue();
    }
}
