package io.casehub.life.api;

import io.casehub.life.api.request.CreateExternalActorRequest;
import io.casehub.life.api.request.UpdateExternalActorRequest;
import io.casehub.life.api.response.ExternalActorResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalActorDtoTest {

    @Test
    void createRequest_holdsAllFields() {
        var req = new CreateExternalActorRequest("Bob's Plumbing", LifeActorType.EXTERNAL_HUMAN, "phone", "+44-7700-900100");
        assertThat(req.name()).isEqualTo("Bob's Plumbing");
        assertThat(req.actorType()).isEqualTo(LifeActorType.EXTERNAL_HUMAN);
        assertThat(req.contactMethod()).isEqualTo("phone");
        assertThat(req.contactValue()).isEqualTo("+44-7700-900100");
    }

    @Test
    void updateRequest_holdsAllFields() {
        var req = new UpdateExternalActorRequest("New Name", LifeActorType.AI_AGENT, "email", "ai@agent.local");
        assertThat(req.name()).isEqualTo("New Name");
    }

    @Test
    void response_holdsAllFields() {
        var id = UUID.randomUUID();
        var now = Instant.now();
        var resp = new ExternalActorResponse(id, "Bob's Plumbing", LifeActorType.EXTERNAL_HUMAN, "phone", "+44-7700-900100", now);
        assertThat(resp.id()).isEqualTo(id);
        assertThat(resp.createdAt()).isEqualTo(now);
    }
}
