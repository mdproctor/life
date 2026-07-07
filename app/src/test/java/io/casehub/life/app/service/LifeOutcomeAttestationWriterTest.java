package io.casehub.life.app.service;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.CapabilityTag;
import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.life.api.LifeCapabilities;
import io.casehub.life.api.LifeDomain;
import io.casehub.life.api.LifeTrustDimensions;
import io.casehub.life.app.LifeDecisionEventType;
import io.casehub.life.app.entity.LifeTaskContext;
import io.casehub.life.app.service.ledger.LifeOutcomeAttestationWriter;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.work.runtime.model.WorkItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit test for LifeOutcomeAttestationWriter — attestation pipeline from WorkItem
 * outcomes to LedgerAttestation records.
 */
@ExtendWith(MockitoExtension.class)
class LifeOutcomeAttestationWriterTest {

    @Mock
    private LedgerEntryRepository ledgerRepository;

    @InjectMocks
    private LifeOutcomeAttestationWriter attestationWriter;

    @Test
    void completedTaskProducesSoundAttestation() {
        // Given: a COMPLETED event with an externalActorId
        var entry = createTestEntry();
        var ctx = new LifeTaskContext();
        ctx.workItemId = UUID.randomUUID();
        ctx.domain = LifeDomain.HEALTH;
        ctx.externalActorId = UUID.randomUUID();

        var workItem = new WorkItem();
        workItem.scope = "casehubio/life/health";

        // When: attestOutcome is called
        attestationWriter.attestOutcome(entry, LifeDecisionEventType.COMPLETED, ctx, workItem);

        // Then: a SOUND verdict attestation is saved
        var captor = ArgumentCaptor.forClass(LedgerAttestation.class);
        verify(ledgerRepository, atLeastOnce()).saveAttestation(captor.capture(), any());

        var verdictAttestations = captor.getAllValues().stream()
                .filter(a -> a.trustDimension == null)
                .toList();

        assertThat(verdictAttestations).hasSize(1);
        var attestation = verdictAttestations.get(0);

        assertThat(attestation.ledgerEntryId).isEqualTo(entry.id);
        assertThat(attestation.subjectId).isEqualTo(ctx.externalActorId);
        assertThat(attestation.attestorId).isEqualTo("life-system");
        assertThat(attestation.attestorType).isEqualTo(ActorType.SYSTEM);
        assertThat(attestation.attestorRole).isEqualTo("OutcomeAssessor");
        assertThat(attestation.verdict).isEqualTo(AttestationVerdict.SOUND);
        assertThat(attestation.confidence).isEqualTo(0.9);
        assertThat(attestation.capabilityTag).isEqualTo(LifeCapabilities.HEALTH_COORDINATION);
    }

    @Test
    void slaBreachProducesFlaggedAttestation() {
        // Given: an SLA_BREACH event
        var entry = createTestEntry();
        var ctx = new LifeTaskContext();
        ctx.workItemId = UUID.randomUUID();
        ctx.domain = LifeDomain.CONTRACTOR_COORDINATION;
        ctx.externalActorId = UUID.randomUUID();

        var workItem = new WorkItem();

        // When: attestOutcome is called
        attestationWriter.attestOutcome(entry, LifeDecisionEventType.SLA_BREACH, ctx, workItem);

        // Then: a FLAGGED verdict attestation is saved
        var captor = ArgumentCaptor.forClass(LedgerAttestation.class);
        verify(ledgerRepository, atLeastOnce()).saveAttestation(captor.capture(), any());

        var verdictAttestations = captor.getAllValues().stream()
                .filter(a -> a.trustDimension == null)
                .toList();

        assertThat(verdictAttestations).hasSize(1);
        var attestation = verdictAttestations.get(0);

        assertThat(attestation.verdict).isEqualTo(AttestationVerdict.FLAGGED);
        assertThat(attestation.confidence).isEqualTo(0.9);
        assertThat(attestation.capabilityTag).isEqualTo(LifeCapabilities.CONTRACTOR_COORDINATION);
    }

    @Test
    void completedTaskOnTimeProducesDeadlineReliabilityDimensionScore() {
        // Given: a COMPLETED event with expiresAt in the future (completed early)
        var entry = createTestEntry();
        var ctx = new LifeTaskContext();
        ctx.workItemId = UUID.randomUUID();
        ctx.domain = LifeDomain.LEGAL;
        ctx.externalActorId = UUID.randomUUID();

        var now = Instant.now();
        var workItem = new WorkItem();
        workItem.expiresAt = now.plus(3, ChronoUnit.DAYS); // deadline 3 days away
        workItem.completedAt = now; // completed now (3 days early)

        // When: attestOutcome is called
        attestationWriter.attestOutcome(entry, LifeDecisionEventType.COMPLETED, ctx, workItem);

        // Then: a deadline-reliability dimension attestation is saved with score 1.0
        var captor = ArgumentCaptor.forClass(LedgerAttestation.class);
        verify(ledgerRepository, atLeastOnce()).saveAttestation(captor.capture(), any());

        var dimensionAttestations = captor.getAllValues().stream()
                .filter(a -> a.trustDimension != null)
                .toList();

        assertThat(dimensionAttestations).hasSize(1);
        var attestation = dimensionAttestations.get(0);

        assertThat(attestation.verdict).isEqualTo(AttestationVerdict.SOUND); // Dimension attestations still have verdict
        assertThat(attestation.trustDimension).isEqualTo(LifeTrustDimensions.DEADLINE_RELIABILITY);
        assertThat(attestation.dimensionScore).isEqualTo(1.0);
        assertThat(attestation.subjectId).isEqualTo(ctx.externalActorId);
    }

    @Test
    void noExternalActorProducesNoAttestation() {
        // Given: a task context with no externalActorId
        var entry = createTestEntry();
        var ctx = new LifeTaskContext();
        ctx.workItemId = UUID.randomUUID();
        ctx.domain = LifeDomain.HOUSEHOLD;
        ctx.externalActorId = null; // no external actor

        var workItem = new WorkItem();

        // When: attestOutcome is called
        attestationWriter.attestOutcome(entry, LifeDecisionEventType.COMPLETED, ctx, workItem);

        // Then: saveAttestation is never called
        verify(ledgerRepository, never()).saveAttestation(any(), any());
    }

    @Test
    void createdEventProducesNoAttestation() {
        var entry = createTestEntry();
        var ctx = new LifeTaskContext();
        ctx.workItemId = UUID.randomUUID();
        ctx.domain = LifeDomain.HEALTH;
        ctx.externalActorId = UUID.randomUUID();

        var workItem = new WorkItem();

        attestationWriter.attestOutcome(entry, LifeDecisionEventType.CREATED, ctx, workItem);

        verify(ledgerRepository, never()).saveAttestation(any(), any());
    }

    @Test
    void unknownScopeUsesGlobalCapabilityTag() {
        // Given: no domain and no parseable scope
        var entry = createTestEntry();
        var ctx = new LifeTaskContext();
        ctx.workItemId = UUID.randomUUID();
        ctx.domain = null; // no domain
        ctx.externalActorId = UUID.randomUUID();

        var workItem = new WorkItem();
        workItem.scope = null; // no scope

        // When: attestOutcome is called
        attestationWriter.attestOutcome(entry, LifeDecisionEventType.COMPLETED, ctx, workItem);

        // Then: capabilityTag is "*" (GLOBAL)
        var captor = ArgumentCaptor.forClass(LedgerAttestation.class);
        verify(ledgerRepository, atLeastOnce()).saveAttestation(captor.capture(), any());

        var verdictAttestations = captor.getAllValues().stream()
                .filter(a -> a.trustDimension == null)
                .toList();

        assertThat(verdictAttestations).hasSize(1);
        assertThat(verdictAttestations.get(0).capabilityTag).isEqualTo(CapabilityTag.GLOBAL);
    }

    private LedgerEntry createTestEntry() {
        var entry = new LedgerEntry() {}; // anonymous subclass
        entry.id = UUID.randomUUID();
        entry.subjectId = UUID.randomUUID();
        return entry;
    }
}
