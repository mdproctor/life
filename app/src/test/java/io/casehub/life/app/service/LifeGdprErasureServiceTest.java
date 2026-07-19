package io.casehub.life.app.service;

import io.casehub.ledger.api.model.ErasureReason;
import io.casehub.ledger.runtime.config.LedgerConfig;
import io.casehub.ledger.runtime.privacy.LedgerErasureService;
import io.casehub.life.api.LifeActorIds;
import io.casehub.life.api.LifeActorType;
import io.casehub.life.api.response.ErasureResponse;
import io.casehub.life.app.entity.ExternalActor;
import io.casehub.life.app.service.ledger.LifeLedgerWriter;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.MemoryCapability;
import io.casehub.neocortex.memory.MemoryCapabilityException;
import io.casehub.platform.api.identity.TenancyConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LifeGdprErasureServiceTest {

    @Mock LedgerErasureService ledgerErasureService;
    @Mock CaseMemoryStore memoryStore;
    @Mock LifeLedgerWriter lifeLedgerWriter;
    @Mock LedgerConfig ledgerConfig;
    @Mock LedgerConfig.IdentityConfig identityConfig;
    @Mock LedgerConfig.IdentityConfig.TokenisationConfig tokenisationConfig;

    private static final UUID ACTOR_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(ledgerConfig.identity()).thenReturn(identityConfig);
        lenient().when(identityConfig.tokenisation()).thenReturn(tokenisationConfig);
        lenient().when(tokenisationConfig.enabled()).thenReturn(true);
        lenient().when(ledgerErasureService.erase(any(), any()))
                .thenReturn(new LedgerErasureService.ErasureResult(
                        LifeActorIds.of(ACTOR_ID), true, 5, Optional.empty()));
    }

    @Test
    void erase_nullifiesPiiAndWritesLedgerEntry() {
        ExternalActor actor = seedActor();
        LifeGdprErasureService service = createServiceWithActor(actor);

        ErasureResponse response = service.erase(ACTOR_ID, "jane.admin");

        assertThat(actor.name).isEqualTo("[ERASED]");
        assertThat(actor.contactValue).isEqualTo("[ERASED]");
        assertThat(actor.gdprErasedAt).isNotNull();
        assertThat(response.erasedActorId()).isEqualTo(ACTOR_ID);
        assertThat(response.ledgerEntriesAffected()).isEqualTo(5);
        assertThat(response.tokenisationEnabled()).isTrue();
        verify(lifeLedgerWriter).writeErasureEntry(eq(actor), eq("jane.admin"), eq(0), eq(5L));
    }

    @Test
    void erase_callsLedgerErasureService() {
        LifeGdprErasureService service = createServiceWithActor(seedActor());

        service.erase(ACTOR_ID, "jane.admin");

        verify(ledgerErasureService).erase(
                eq(LifeActorIds.of(ACTOR_ID)),
                eq(ErasureReason.GDPR_ART_17_REQUEST));
    }

    @Test
    void erase_callsMemoryStoreErase() {
        LifeGdprErasureService service = createServiceWithActor(seedActor());

        service.erase(ACTOR_ID, "jane.admin");

        verify(memoryStore).eraseEntity(
                eq(LifeActorIds.of(ACTOR_ID)),
                eq(TenancyConstants.DEFAULT_TENANT_ID));
    }

    @Test
    void erase_handlesMemoryCapabilityException() {
        when(memoryStore.eraseEntity(any(), any()))
                .thenThrow(new MemoryCapabilityException(MemoryCapability.ERASE_ENTITY, CaseMemoryStore.class));
        LifeGdprErasureService service = createServiceWithActor(seedActor());

        ErasureResponse response = service.erase(ACTOR_ID, "jane.admin");

        assertThat(response.memoryRecordsErased()).isZero();
    }

    @Test
    void erase_throws404_whenActorNotFound() {
        LifeGdprErasureService service = createServiceWithActor(null);

        assertThatThrownBy(() -> service.erase(UUID.randomUUID(), "jane.admin"))
                .isInstanceOf(jakarta.ws.rs.NotFoundException.class);
    }

    @Test
    void erase_throws409_whenAlreadyErased() {
        ExternalActor actor = seedActor();
        actor.gdprErasedAt = Instant.now();
        LifeGdprErasureService service = createServiceWithActor(actor);

        assertThatThrownBy(() -> service.erase(ACTOR_ID, "jane.admin"))
                .isInstanceOf(jakarta.ws.rs.WebApplicationException.class)
                .hasMessageContaining("already erased");
    }

    @Test
    void erase_reportsTokenisationDisabledWhenConfigSaysSo() {
        when(tokenisationConfig.enabled()).thenReturn(false);
        LifeGdprErasureService service = createServiceWithActor(seedActor());

        ErasureResponse response = service.erase(ACTOR_ID, "jane.admin");

        assertThat(response.tokenisationEnabled()).isFalse();
    }

    // Note: active-task guard test requires Panache static methods (LifeTaskContext.list),
    // which cannot be mocked in unit tests. Covered by ExternalActorGdprResourceTest (@QuarkusTest).

    // ── Helpers ────────────────────────────────────────────────────────────

    private ExternalActor seedActor() {
        var a = new ExternalActor();
        a.id = ACTOR_ID;
        a.name = "Bob Contractor";
        a.contactMethod = "phone";
        a.contactValue = "+44-7700-900001";
        a.actorType = LifeActorType.EXTERNAL_HUMAN;
        return a;
    }

    /**
     * Override findActor() and findActiveTaskCount() to bypass Panache static methods
     * (GE-20260629-74fc65). Same pattern as LegalDomainLedgerHandler.findContext().
     */
    private LifeGdprErasureService createServiceWithActor(ExternalActor actor) {
        return new LifeGdprErasureService(ledgerErasureService, memoryStore, lifeLedgerWriter, ledgerConfig) {
            @Override
            protected ExternalActor findActor(UUID id) {
                if (actor == null || !actor.id.equals(id)) {
                    throw new jakarta.ws.rs.NotFoundException();
                }
                return actor;
            }

            @Override
            protected long findActiveTaskCount(UUID externalActorId) {
                return 0;
            }
        };
    }
}
