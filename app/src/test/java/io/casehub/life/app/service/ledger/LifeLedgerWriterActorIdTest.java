package io.casehub.life.app.service.ledger;

import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.life.api.LifeActorIds;
import io.casehub.life.api.LifeDomain;
import io.casehub.life.app.LifeDecisionEventType;
import io.casehub.life.app.entity.LifeTaskContext;
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

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LifeLedgerWriterActorIdTest {

    @Mock LedgerEntryRepository ledgerRepository;
    @Mock LifeOutcomeAttestationWriter attestationWriter;
    @InjectMocks LifeLedgerWriter writer;

    private static final UUID WORK_ITEM_ID = UUID.randomUUID();
    private static final UUID EXTERNAL_ACTOR_ID = UUID.randomUUID();
    private static final Instant SLA = Instant.parse("2026-06-01T09:00:00Z");

    @BeforeEach
    void setUp() {
        when(ledgerRepository.findLatestBySubjectId(any())).thenReturn(Optional.empty());
        when(ledgerRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    // ── Health Entry ActorId ───────────────────────────────────────────────

    @Test
    void writeHealthEntry_withExternalActor_usesLifeActorId() {
        var ctx = healthCtx(EXTERNAL_ACTOR_ID);
        var wi = workItem();

        writer.writeHealthEntry(LifeDecisionEventType.CREATED, ctx, wi);

        var entry = captureHealth();
        assertThat(entry.actorId).isEqualTo(LifeActorIds.of(EXTERNAL_ACTOR_ID));
        assertThat(entry.actorType).isEqualTo(ActorType.HUMAN);
    }

    @Test
    void writeHealthEntry_withoutExternalActor_usesLifeSystem() {
        var ctx = healthCtx(null);
        var wi = workItem();

        writer.writeHealthEntry(LifeDecisionEventType.CREATED, ctx, wi);

        var entry = captureHealth();
        assertThat(entry.actorId).isEqualTo("life-system");
        assertThat(entry.actorType).isEqualTo(ActorType.SYSTEM);
    }

    @Test
    void writeHealthEntry_callsAttestationWriter() {
        var ctx = healthCtx(EXTERNAL_ACTOR_ID);
        var wi = workItem();

        writer.writeHealthEntry(LifeDecisionEventType.COMPLETED, ctx, wi);

        var entry = captureHealth();
        verify(attestationWriter).attestOutcome(entry, LifeDecisionEventType.COMPLETED, ctx, wi);
    }

    // ── Legal Entry ActorId ────────────────────────────────────────────────

    @Test
    void writeLegalEntry_withExternalActor_usesLifeActorId() {
        var ctx = legalCtx(EXTERNAL_ACTOR_ID);
        var wi = workItem();

        writer.writeLegalEntry(LifeDecisionEventType.CREATED, ctx, wi);

        var entry = captureLegal();
        assertThat(entry.actorId).isEqualTo(LifeActorIds.of(EXTERNAL_ACTOR_ID));
        assertThat(entry.actorType).isEqualTo(ActorType.HUMAN);
    }

    @Test
    void writeLegalEntry_withoutExternalActor_usesLifeSystem() {
        var ctx = legalCtx(null);
        var wi = workItem();

        writer.writeLegalEntry(LifeDecisionEventType.CREATED, ctx, wi);

        var entry = captureLegal();
        assertThat(entry.actorId).isEqualTo("life-system");
        assertThat(entry.actorType).isEqualTo(ActorType.SYSTEM);
    }

    @Test
    void writeLegalEntry_callsAttestationWriter() {
        var ctx = legalCtx(EXTERNAL_ACTOR_ID);
        var wi = workItem();

        writer.writeLegalEntry(LifeDecisionEventType.SLA_BREACH, ctx, wi);

        var entry = captureLegal();
        verify(attestationWriter).attestOutcome(entry, LifeDecisionEventType.SLA_BREACH, ctx, wi);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private LifeTaskContext healthCtx(UUID externalActorId) {
        var ctx = new LifeTaskContext();
        ctx.workItemId = WORK_ITEM_ID;
        ctx.domain = LifeDomain.HEALTH;
        ctx.externalActorId = externalActorId;
        return ctx;
    }

    private LifeTaskContext legalCtx(UUID externalActorId) {
        var ctx = new LifeTaskContext();
        ctx.workItemId = WORK_ITEM_ID;
        ctx.domain = LifeDomain.LEGAL;
        ctx.externalActorId = externalActorId;
        return ctx;
    }

    private WorkItem workItem() {
        var wi = new WorkItem();
        wi.id = WORK_ITEM_ID;
        wi.category = "test-category";
        wi.expiresAt = SLA;
        wi.title = "Test Task";
        return wi;
    }

    private HealthDecisionLedgerEntry captureHealth() {
        var cap = ArgumentCaptor.forClass(HealthDecisionLedgerEntry.class);
        verify(ledgerRepository).save(cap.capture());
        return cap.getValue();
    }

    private LegalActionLedgerEntry captureLegal() {
        var cap = ArgumentCaptor.forClass(LegalActionLedgerEntry.class);
        verify(ledgerRepository).save(cap.capture());
        return cap.getValue();
    }
}
