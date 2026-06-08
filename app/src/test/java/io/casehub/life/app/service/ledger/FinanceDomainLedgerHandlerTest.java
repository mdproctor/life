package io.casehub.life.app.service.ledger;

import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.life.api.LifeDomain;
import io.casehub.life.app.LifeDecisionEventType;
import io.casehub.life.app.entity.LifeCommitmentRecord;
import io.casehub.life.app.ledger.FinancialDecisionLedgerEntry;
import io.casehub.work.runtime.model.WorkItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinanceDomainLedgerHandlerTest {

    @Mock LedgerEntryRepository ledgerRepository;

    FinanceDomainLedgerHandler handler;

    @BeforeEach
    void setUp() {
        handler = new FinanceDomainLedgerHandler(ledgerRepository);
    }

    @Test void domain_isFinance() {
        assertEquals(LifeDomain.FINANCE, handler.domain());
    }

    @Test void writeEntry_task_created_isNoOp() {
        UUID taskId = UUID.randomUUID();
        handler.writeEntry(LifeDecisionEventType.CREATED, taskId, new WorkItem());
        verify(ledgerRepository, never()).save(any());
    }

    @Test void writeEntry_task_slaBreach_withRecord_savesEntry() {
        UUID taskId = UUID.randomUUID();
        LifeCommitmentRecord record = new LifeCommitmentRecord();
        record.id = UUID.randomUUID();
        record.amountThreshold = BigDecimal.valueOf(500);
        record.purchaseCategory = "appliance";

        WorkItem workItem = new WorkItem();
        workItem.id = taskId;

        handler = new FinanceDomainLedgerHandler(ledgerRepository) {
            @Override protected Optional<LifeCommitmentRecord> findRecord(UUID id) {
                return Optional.of(record);
            }
        };

        when(ledgerRepository.findLatestBySubjectId(any())).thenReturn(Optional.empty());
        when(ledgerRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        handler.writeEntry(LifeDecisionEventType.SLA_BREACH, taskId, workItem);

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerRepository).save(captor.capture());
        assertInstanceOf(FinancialDecisionLedgerEntry.class, captor.getValue());
    }

    @Test void writeEntry_commitment_created_savesEntry() {
        LifeCommitmentRecord record = new LifeCommitmentRecord();
        record.id = UUID.randomUUID();
        record.amountThreshold = BigDecimal.valueOf(1000);
        record.purchaseCategory = "contractor";

        when(ledgerRepository.findLatestBySubjectId(any())).thenReturn(Optional.empty());
        when(ledgerRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        handler.writeEntry(LifeDecisionEventType.CREATED, record);

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerRepository).save(captor.capture());
        FinancialDecisionLedgerEntry entry = (FinancialDecisionLedgerEntry) captor.getValue();
        assertEquals(LifeDecisionEventType.CREATED, entry.eventType);
        assertEquals(BigDecimal.valueOf(1000), entry.amountThreshold);
    }

    @Test void writeEntry_commitment_slaBreach_savesEntry() {
        LifeCommitmentRecord record = new LifeCommitmentRecord();
        record.id = UUID.randomUUID();
        record.amountThreshold = BigDecimal.valueOf(750);
        record.purchaseCategory = "contractor";

        when(ledgerRepository.findLatestBySubjectId(any())).thenReturn(Optional.empty());
        when(ledgerRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        handler.writeEntry(LifeDecisionEventType.SLA_BREACH, record);

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerRepository).save(captor.capture());
        FinancialDecisionLedgerEntry entry = (FinancialDecisionLedgerEntry) captor.getValue();
        assertEquals(LifeDecisionEventType.SLA_BREACH, entry.eventType);
    }
}
