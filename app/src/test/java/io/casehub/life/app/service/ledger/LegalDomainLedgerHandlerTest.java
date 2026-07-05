package io.casehub.life.app.service.ledger;

import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.life.api.LifeDomain;
import io.casehub.life.app.LifeDecisionEventType;
import io.casehub.life.app.entity.LifeTaskContext;
import io.casehub.life.app.ledger.LegalActionLedgerEntry;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.api.WorkItemStatus;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LegalDomainLedgerHandlerTest {

    @Mock LedgerEntryRepository ledgerRepository;
    @Mock LifeOutcomeAttestationWriter attestationWriter;

    LegalDomainLedgerHandler handler;

    @BeforeEach
    void setUp() {
        handler = new LegalDomainLedgerHandler(ledgerRepository, attestationWriter);
    }

    @Test void domain_isLegal() {
        assertEquals(LifeDomain.LEGAL, handler.domain());
    }

    @Test void writeEntry_nullContext_doesNotWrite() {
        UUID taskId = UUID.randomUUID();
        WorkItem workItem = new WorkItem();
        workItem.id = taskId;

        handler = new LegalDomainLedgerHandler(ledgerRepository, attestationWriter) {
            @Override protected Optional<LifeTaskContext> findContext(UUID id) {
                return Optional.empty();
            }
        };
        handler.writeEntry(LifeDecisionEventType.COMPLETED, taskId, workItem);
        verify(ledgerRepository, never()).save(any(), any());
    }

    @Test void writeEntry_slaBreach_savesLegalEntry() {
        UUID taskId = UUID.randomUUID();
        WorkItem workItem = new WorkItem();
        workItem.id = taskId;
        workItem.title = "File tax return";
        workItem.status = WorkItemStatus.EXPIRED;

        LifeTaskContext ctx = new LifeTaskContext();
        ctx.workItemId = taskId;
        ctx.domain = LifeDomain.LEGAL;

        handler = new LegalDomainLedgerHandler(ledgerRepository, attestationWriter) {
            @Override protected Optional<LifeTaskContext> findContext(UUID id) {
                return Optional.of(ctx);
            }
        };

        when(ledgerRepository.findLatestBySubjectId(any(), any())).thenReturn(Optional.empty());
        when(ledgerRepository.save(any(), any())).thenAnswer(i -> i.getArgument(0));

        handler.writeEntry(LifeDecisionEventType.SLA_BREACH, taskId, workItem);

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerRepository).save(captor.capture(), any());
        assertInstanceOf(LegalActionLedgerEntry.class, captor.getValue());
        LegalActionLedgerEntry entry = (LegalActionLedgerEntry) captor.getValue();
        assertEquals("File tax return", entry.legalObligation);
        assertEquals(LifeDecisionEventType.SLA_BREACH, entry.eventType);
    }

    @Test void writeEntry_populatesJurisdictionFromConfig() {
        UUID taskId = UUID.randomUUID();
        WorkItem workItem = new WorkItem();
        workItem.id = taskId;
        workItem.title = "File tax return";

        LifeTaskContext ctx = new LifeTaskContext();
        ctx.workItemId = taskId;
        ctx.domain = LifeDomain.LEGAL;

        handler = new LegalDomainLedgerHandler(ledgerRepository, attestationWriter) {
            @Override protected Optional<LifeTaskContext> findContext(UUID id) {
                return Optional.of(ctx);
            }
        };

        when(ledgerRepository.findLatestBySubjectId(any(), any())).thenReturn(Optional.empty());
        when(ledgerRepository.save(any(), any())).thenAnswer(i -> i.getArgument(0));

        handler.writeEntry(LifeDecisionEventType.COMPLETED, taskId, workItem);

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerRepository).save(captor.capture(), any());
        LegalActionLedgerEntry entry = (LegalActionLedgerEntry) captor.getValue();
        assertEquals("GB", entry.jurisdiction); // Expected to match config default
    }

    @Test void writeEntry_usesContextJurisdiction_whenPresent() {
        UUID taskId = UUID.randomUUID();
        WorkItem workItem = new WorkItem();
        workItem.id = taskId;
        workItem.title = "File immigration paperwork";

        LifeTaskContext ctx = new LifeTaskContext();
        ctx.workItemId = taskId;
        ctx.domain = LifeDomain.LEGAL;
        ctx.jurisdiction = "US-CA";

        handler = new LegalDomainLedgerHandler(ledgerRepository, attestationWriter) {
            @Override protected Optional<LifeTaskContext> findContext(UUID id) {
                return Optional.of(ctx);
            }
        };

        when(ledgerRepository.findLatestBySubjectId(any(), any())).thenReturn(Optional.empty());
        when(ledgerRepository.save(any(), any())).thenAnswer(i -> i.getArgument(0));

        handler.writeEntry(LifeDecisionEventType.COMPLETED, taskId, workItem);

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerRepository).save(captor.capture(), any());
        LegalActionLedgerEntry entry = (LegalActionLedgerEntry) captor.getValue();
        assertEquals("US-CA", entry.jurisdiction);
    }

    @Test void writeEntry_fallsBackToConfig_whenContextJurisdictionNull() {
        UUID taskId = UUID.randomUUID();
        WorkItem workItem = new WorkItem();
        workItem.id = taskId;
        workItem.title = "File tax return";

        LifeTaskContext ctx = new LifeTaskContext();
        ctx.workItemId = taskId;
        ctx.domain = LifeDomain.LEGAL;
        ctx.jurisdiction = null;

        handler = new LegalDomainLedgerHandler(ledgerRepository, attestationWriter) {
            @Override protected Optional<LifeTaskContext> findContext(UUID id) {
                return Optional.of(ctx);
            }
        };

        when(ledgerRepository.findLatestBySubjectId(any(), any())).thenReturn(Optional.empty());
        when(ledgerRepository.save(any(), any())).thenAnswer(i -> i.getArgument(0));

        handler.writeEntry(LifeDecisionEventType.COMPLETED, taskId, workItem);

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerRepository).save(captor.capture(), any());
        LegalActionLedgerEntry entry = (LegalActionLedgerEntry) captor.getValue();
        assertEquals("GB", entry.jurisdiction);
    }
}
