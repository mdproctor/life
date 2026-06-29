package io.casehub.life.app.service.ledger;

import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.life.app.entity.ExternalActor;
import io.casehub.life.app.ledger.ExternalActorErasureLedgerEntry;
import io.casehub.platform.api.identity.ActorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LifeLedgerWriterTest {

    @Mock LedgerEntryRepository ledgerRepository;
    @InjectMocks LifeLedgerWriter writer;

    private static final UUID ACTOR_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(ledgerRepository.findLatestBySubjectId(any(), any())).thenReturn(Optional.empty());
        when(ledgerRepository.save(any(), any())).thenAnswer(i -> i.getArgument(0));
    }

    // ── Erasure ────────────────────────────────────────────────────────────

    @Test
    void writeErasureEntry_setsRequiredFields() {
        writer.writeErasureEntry(externalActor(), "household-admin", 0);

        var entry = captureErasure();
        assertThat(entry.erasedActorId).isEqualTo(ACTOR_ID);
        assertThat(entry.contactMethod).isEqualTo("phone");
        assertThat(entry.erasedBy).isEqualTo("household-admin");
        assertThat(entry.subjectId).isEqualTo(ACTOR_ID);
        assertThat(entry.actorId).isEqualTo("household-admin");
        assertThat(entry.actorType).isEqualTo(ActorType.HUMAN);
        assertThat(entry.actorRole).isEqualTo("GdprDataController");
        assertThat(entry.sequenceNumber).isEqualTo(1);
        assertThat(entry.entryType).isEqualTo(LedgerEntryType.EVENT);
    }

    @Test
    void writeErasureEntry_setsMemoryRecordsErased() {
        writer.writeErasureEntry(externalActor(), "household-admin", 7);

        var entry = captureErasure();
        assertThat(entry.memoryRecordsErased).isEqualTo(7);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private ExternalActor externalActor() {
        var a = new ExternalActor();
        a.id = ACTOR_ID;
        a.contactMethod = "phone";
        a.name = "Bob";
        a.contactValue = "+44-7700-900001";
        return a;
    }

    private ExternalActorErasureLedgerEntry captureErasure() {
        var cap = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerRepository).save(cap.capture(), any());
        return (ExternalActorErasureLedgerEntry) cap.getValue();
    }
}
