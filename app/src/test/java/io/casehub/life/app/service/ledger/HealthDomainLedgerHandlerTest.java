package io.casehub.life.app.service.ledger;

import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.life.api.LifeDomain;
import io.casehub.life.app.LifeDecisionEventType;
import io.casehub.life.app.entity.LifeTaskContext;
import io.casehub.life.app.ledger.HealthDecisionLedgerEntry;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HealthDomainLedgerHandlerTest {

    @Mock LedgerEntryRepository ledgerRepository;
    @Mock LifeOutcomeAttestationWriter attestationWriter;

    HealthDomainLedgerHandler handler;

    @BeforeEach
    void setUp() {
        handler = new HealthDomainLedgerHandler(ledgerRepository, attestationWriter);
    }

    @Test void domain_isHealth() {
        assertEquals(LifeDomain.HEALTH, handler.domain());
    }

    @Test void writeEntry_nullContext_doesNotWrite() {
        UUID taskId = UUID.randomUUID();
        WorkItem workItem = new WorkItem();
        workItem.id = taskId;

        handler = new HealthDomainLedgerHandler(ledgerRepository, attestationWriter) {
            @Override protected Optional<LifeTaskContext> findContext(UUID id) {
                return Optional.empty();
            }
        };
        handler.writeEntry(LifeDecisionEventType.COMPLETED, taskId, workItem);
        verify(ledgerRepository, never()).save(any(), any());
    }

    @Test void writeEntry_completed_savesHealthEntry() {
        UUID taskId = UUID.randomUUID();
        WorkItem workItem = new WorkItem();
        workItem.id = taskId;
        workItem.status = WorkItemStatus.COMPLETED;
        workItem.outcome = "appointment-attended";

        LifeTaskContext ctx = new LifeTaskContext();
        ctx.workItemId = taskId;
        ctx.domain = LifeDomain.HEALTH;

        handler = new HealthDomainLedgerHandler(ledgerRepository, attestationWriter) {
            @Override protected Optional<LifeTaskContext> findContext(UUID id) {
                return Optional.of(ctx);
            }
        };

        when(ledgerRepository.findLatestBySubjectId(any(), any())).thenReturn(Optional.empty());
        when(ledgerRepository.save(any(), any())).thenAnswer(i -> i.getArgument(0));

        handler.writeEntry(LifeDecisionEventType.COMPLETED, taskId, workItem);

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerRepository).save(captor.capture(), any());
        assertInstanceOf(HealthDecisionLedgerEntry.class, captor.getValue());
        HealthDecisionLedgerEntry entry = (HealthDecisionLedgerEntry) captor.getValue();
        assertEquals("appointment-attended", entry.outcome);
        assertEquals(LifeDecisionEventType.COMPLETED, entry.eventType);
    }

    @Test void writeEntry_slaBreach_outcomeIsNull() {
        UUID taskId = UUID.randomUUID();
        WorkItem workItem = new WorkItem();
        workItem.id = taskId;
        workItem.outcome = "should-not-appear";

        LifeTaskContext ctx = new LifeTaskContext();
        ctx.workItemId = taskId;
        ctx.domain = LifeDomain.HEALTH;

        handler = new HealthDomainLedgerHandler(ledgerRepository, attestationWriter) {
            @Override protected Optional<LifeTaskContext> findContext(UUID id) {
                return Optional.of(ctx);
            }
        };

        when(ledgerRepository.findLatestBySubjectId(any(), any())).thenReturn(Optional.empty());
        when(ledgerRepository.save(any(), any())).thenAnswer(i -> i.getArgument(0));

        handler.writeEntry(LifeDecisionEventType.SLA_BREACH, taskId, workItem);

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerRepository).save(captor.capture(), any());
        assertNull(((HealthDecisionLedgerEntry) captor.getValue()).outcome);
    }

    @Test void writeEntry_completed_callsAttestation() {
        UUID taskId = UUID.randomUUID();
        WorkItem workItem = new WorkItem();
        workItem.id = taskId;
        workItem.outcome = "done";

        LifeTaskContext ctx = new LifeTaskContext();
        ctx.workItemId = taskId;
        ctx.domain = LifeDomain.HEALTH;

        handler = new HealthDomainLedgerHandler(ledgerRepository, attestationWriter) {
            @Override protected Optional<LifeTaskContext> findContext(UUID id) {
                return Optional.of(ctx);
            }
        };

        when(ledgerRepository.findLatestBySubjectId(any(), any())).thenReturn(Optional.empty());
        when(ledgerRepository.save(any(), any())).thenAnswer(i -> i.getArgument(0));

        handler.writeEntry(LifeDecisionEventType.COMPLETED, taskId, workItem);
        verify(attestationWriter).attestOutcome(any(), eq(LifeDecisionEventType.COMPLETED), eq(ctx), eq(workItem));
    }
}
