package io.casehub.life.api;

import io.casehub.life.api.request.CreateLifeTaskRequest;
import io.casehub.life.api.response.LifeTaskResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LifeTaskDtoTest {

    @Test
    void createRequest_holdsAllFields() {
        var actorId = UUID.randomUUID();
        var deadline = Instant.now().plusSeconds(3600);
        var req = new CreateLifeTaskRequest("household-task", "Fix boiler", actorId, deadline);

        assertThat(req.templateRef()).isEqualTo("household-task");
        assertThat(req.title()).isEqualTo("Fix boiler");
        assertThat(req.externalActorId()).isEqualTo(actorId);
        assertThat(req.deadline()).isEqualTo(deadline);
    }

    @Test
    void createRequest_nullOptionalFields_isValid() {
        var req = new CreateLifeTaskRequest("household-task", "Fix boiler", null, null);
        assertThat(req.externalActorId()).isNull();
        assertThat(req.deadline()).isNull();
    }

    @Test
    void response_holdsAllFields() {
        var workItemId = UUID.randomUUID();
        var actorId = UUID.randomUUID();
        var now = Instant.now();
        var resp = new LifeTaskResponse(workItemId, "household-task", LifeDomain.HOUSEHOLD,
                "PENDING", actorId, now, null, null);

        assertThat(resp.workItemId()).isEqualTo(workItemId);
        assertThat(resp.domain()).isEqualTo(LifeDomain.HOUSEHOLD);
        assertThat(resp.status()).isEqualTo("PENDING");
    }
}
