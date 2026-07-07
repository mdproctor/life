package io.casehub.life.app.service.ledger;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.CapabilityTag;
import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.life.api.LifeDomain;
import io.casehub.life.api.LifeTrustDimensions;
import io.casehub.life.app.LifeDecisionEventType;
import io.casehub.life.app.entity.LifeTaskContext;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.work.runtime.model.WorkItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;

/**
 * Converts WorkItem outcomes and SLA breaches into LedgerAttestation records.
 * <p>
 * Produces verdict attestations (SOUND for COMPLETED, FLAGGED for SLA_BREACH) and
 * deadline-reliability dimension scores for tasks with deadlines.
 */
@ApplicationScoped
public class LifeOutcomeAttestationWriter {

    @Inject
    LedgerEntryRepository ledgerRepository;

    public void attestOutcome(final LedgerEntry entry,
                              final LifeDecisionEventType eventType,
                              final LifeTaskContext ctx,
                              final WorkItem workItem) {
        if (ctx.externalActorId == null) {
            return;
        }
        if (eventType == LifeDecisionEventType.CREATED) {
            return;
        }

        var capabilityTag = resolveCapabilityTag(ctx, workItem);
        var verdict = eventType == LifeDecisionEventType.COMPLETED
                ? AttestationVerdict.SOUND
                : AttestationVerdict.FLAGGED;

        // Save verdict attestation
        saveVerdictAttestation(entry, ctx, verdict, capabilityTag);

        // Save deadline-reliability dimension attestation if applicable
        if (workItem.expiresAt != null) {
            saveDeadlineReliabilityAttestation(entry, ctx, workItem, verdict);
        }
    }

    private void saveVerdictAttestation(final LedgerEntry entry,
                                        final LifeTaskContext ctx,
                                        final AttestationVerdict verdict,
                                        final String capabilityTag) {
        var attestation = new LedgerAttestation();
        attestation.ledgerEntryId = entry.id;
        attestation.subjectId = ctx.externalActorId;
        attestation.attestorId = "life-system";
        attestation.attestorType = ActorType.SYSTEM;
        attestation.attestorRole = "OutcomeAssessor";
        attestation.verdict = verdict;
        attestation.confidence = 0.9;
        attestation.capabilityTag = capabilityTag;

        ledgerRepository.saveAttestation(attestation, TenancyConstants.DEFAULT_TENANT_ID);
    }

    private void saveDeadlineReliabilityAttestation(final LedgerEntry entry,
                                                    final LifeTaskContext ctx,
                                                    final WorkItem workItem,
                                                    final AttestationVerdict verdict) {
        var completionTime = workItem.completedAt != null ? workItem.completedAt : Instant.now();
        var daysLate = Duration.between(workItem.expiresAt, completionTime).toDays();
        var score = clamp(1.0 - daysLate / 7.0, 0.0, 1.0);

        var attestation = new LedgerAttestation();
        attestation.ledgerEntryId = entry.id;
        attestation.subjectId = ctx.externalActorId;
        attestation.attestorId = "life-system";
        attestation.attestorType = ActorType.SYSTEM;
        attestation.attestorRole = "OutcomeAssessor";
        attestation.verdict = verdict; // Dimension attestations still need a verdict
        attestation.trustDimension = LifeTrustDimensions.DEADLINE_RELIABILITY;
        attestation.dimensionScore = score;

        ledgerRepository.saveAttestation(attestation, TenancyConstants.DEFAULT_TENANT_ID);
    }

    private String resolveCapabilityTag(final LifeTaskContext ctx, final WorkItem workItem) {
        if (ctx.domain != null) {
            return ctx.domain.descriptor().capability();
        }
        if (workItem.scope != null) {
            String[] segments = workItem.scope.split("/");
            if (segments.length >= 3) {
                try {
                    return LifeDomain.valueOf(segments[2].toUpperCase()).descriptor().capability();
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return CapabilityTag.GLOBAL;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
