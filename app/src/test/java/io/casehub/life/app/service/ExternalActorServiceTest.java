package io.casehub.life.app.service;

import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.life.api.LifeActorIds;
import io.casehub.life.api.LifeActorType;
import io.casehub.life.app.LifeTestFixtures;
import io.casehub.life.app.entity.ExternalActor;
import io.casehub.life.app.ledger.ExternalActorErasureLedgerEntry;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.memory.CaseMemoryStore;
import io.casehub.platform.api.memory.MemoryCapability;
import io.casehub.platform.api.memory.MemoryCapabilityException;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestSecurity(user = "test-eraser", roles = {"household-admin"})
class ExternalActorServiceTest {

    @InjectMock CaseMemoryStore memoryStore;
    @Inject ExternalActorService service;
    @Inject LedgerEntryRepository ledgerRepository;

    @BeforeEach
    @Transactional
    void setUp() {
        LifeTestFixtures.seedStandardTemplates();
    }

    @Test
    void erase_callsEraseEntityWithCorrectArgs() {
        final UUID actorId = createActor();

        service.erase(actorId, "jane.admin");

        verify(memoryStore).eraseEntity(
                eq(LifeActorIds.of(actorId)),
                eq(TenancyConstants.DEFAULT_TENANT_ID));
    }

    @Test
    void erase_passesMemoryCountToLedgerWriter() {
        final UUID actorId = createActor();
        when(memoryStore.eraseEntity(any(), any())).thenReturn(5);

        service.erase(actorId, "jane.admin");

        var entry = (ExternalActorErasureLedgerEntry) ledgerRepository
                .findLatestBySubjectId(actorId, TenancyConstants.DEFAULT_TENANT_ID).orElseThrow();
        assertThat(entry.memoryRecordsErased).isEqualTo(5);
    }

    @Test
    void erase_catchesMemoryCapabilityException_countsAsZero() {
        final UUID actorId = createActor();
        when(memoryStore.eraseEntity(any(), any()))
                .thenThrow(new MemoryCapabilityException(MemoryCapability.ERASE_ENTITY, CaseMemoryStore.class));

        service.erase(actorId, "jane.admin");

        var entry = (ExternalActorErasureLedgerEntry) ledgerRepository
                .findLatestBySubjectId(actorId, TenancyConstants.DEFAULT_TENANT_ID).orElseThrow();
        assertThat(entry.memoryRecordsErased).isZero();
    }

    @Test
    void erase_passesErasedByToLedgerWriter() {
        final UUID actorId = createActor();

        service.erase(actorId, "custom-actor-id");

        var entry = (ExternalActorErasureLedgerEntry) ledgerRepository
                .findLatestBySubjectId(actorId, TenancyConstants.DEFAULT_TENANT_ID).orElseThrow();
        assertThat(entry.erasedBy).isEqualTo("custom-actor-id");
    }

    @Transactional
    UUID createActor() {
        var actor = new ExternalActor();
        actor.name = "Test Actor-" + UUID.randomUUID().toString().substring(0, 8);
        actor.actorType = LifeActorType.EXTERNAL_HUMAN;
        actor.contactMethod = "phone";
        actor.contactValue = "+44-7700-900001";
        actor.persist();
        return actor.id;
    }
}
