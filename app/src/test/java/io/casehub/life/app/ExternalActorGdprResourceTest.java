package io.casehub.life.app;

import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.life.api.LifeActorType;
import io.casehub.life.api.LifeDomain;
import io.casehub.life.app.entity.ExternalActor;
import io.casehub.life.app.entity.LifeTaskContext;
import io.casehub.work.runtime.model.WorkItemCreateRequest;
import io.casehub.work.runtime.model.WorkItemPriority;
import io.casehub.work.runtime.service.WorkItemService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class ExternalActorGdprResourceTest {

    @Inject LedgerEntryRepository ledgerRepository;
    @Inject WorkItemService workItemService;

    @BeforeEach
    @Transactional
    void seedTemplate() {
        LifeTestFixtures.seedStandardTemplates();
    }

    @Test
    void eraseActor_204_and_piiNulled() {
        final UUID actorId = createActor();

        given()
                .when().delete("/external-actors/" + actorId + "/personal-data")
                .then().statusCode(204);

        final ExternalActor persisted = ExternalActor.findById(actorId);
        assertThat(persisted.name).isEqualTo("[ERASED]");
        assertThat(persisted.contactValue).isEqualTo("[ERASED]");
        assertThat(persisted.gdprErasedAt).isNotNull();
    }

    @Test
    void eraseActor_writesErasureLedgerEntry() {
        final UUID actorId = createActor();

        given()
                .when().delete("/external-actors/" + actorId + "/personal-data")
                .then().statusCode(204);

        var entry = ledgerRepository.findLatestBySubjectId(actorId).orElseThrow();
        assertThat(entry).isInstanceOf(io.casehub.life.app.ledger.ExternalActorErasureLedgerEntry.class);
        var erasure = (io.casehub.life.app.ledger.ExternalActorErasureLedgerEntry) entry;
        assertThat(erasure.erasedActorId).isEqualTo(actorId);
        assertThat(erasure.erasedBy).isEqualTo("household-admin");
        assertThat(erasure.contactMethod).isEqualTo("phone");
    }

    @Test
    void eraseActor_404_whenNotFound() {
        given()
                .when().delete("/external-actors/" + UUID.randomUUID() + "/personal-data")
                .then().statusCode(404);
    }

    @Test
    void eraseActor_409_whenAlreadyErased() {
        final UUID actorId = createActor();
        given().when().delete("/external-actors/" + actorId + "/personal-data").then().statusCode(204);
        given().when().delete("/external-actors/" + actorId + "/personal-data").then().statusCode(409);
    }

    @Test
    void eraseActor_409_whenActiveTasksExist() {
        final UUID actorId = createActor();
        createActiveTaskForActor(actorId);

        given()
                .when().delete("/external-actors/" + actorId + "/personal-data")
                .then().statusCode(409);
    }

    @Test
    void eraseActor_allowsErasureWhenTasksAreCompleted() {
        final UUID actorId = createActor();
        createCompletedTaskForActor(actorId);

        given()
                .when().delete("/external-actors/" + actorId + "/personal-data")
                .then().statusCode(204);
    }

    @Test
    void getActor_includesGdprErasedAt_afterErasure() {
        final UUID actorId = createActor();
        given().when().delete("/external-actors/" + actorId + "/personal-data").then().statusCode(204);

        given()
                .when().get("/external-actors/" + actorId)
                .then()
                .statusCode(200)
                .body("gdprErasedAt", notNullValue())
                .body("name", equalTo("[ERASED]"))
                .body("trustProfile.globalScore", nullValue())
                .body("trustProfile.dimensionScores", org.hamcrest.Matchers.aMapWithSize(0))
                .body("trustProfile.capabilityScores", org.hamcrest.Matchers.aMapWithSize(0));
    }

    @Test
    void getActor_gdprErasedAtIsNull_beforeErasure() {
        final UUID actorId = createActor();
        given()
                .when().get("/external-actors/" + actorId)
                .then()
                .statusCode(200)
                .body("gdprErasedAt", nullValue());
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    @Transactional
    UUID createActor() {
        var actor = new ExternalActor();
        actor.name = "Bob Contractor-" + UUID.randomUUID().toString().substring(0, 8);
        actor.actorType = LifeActorType.EXTERNAL_HUMAN;
        actor.contactMethod = "phone";
        actor.contactValue = "+44-7700-900999";
        actor.persist();
        return actor.id;
    }

    @Transactional
    void createCompletedTaskForActor(UUID actorId) {
        var req = WorkItemCreateRequest.builder()
                .title("Completed task")
                .category("household")
                .priority(WorkItemPriority.MEDIUM)
                .candidateGroups("household-member")
                .createdBy("life-system")
                .callerRef("life:task/household-task")
                .scope("casehubio/life/household")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        var wi = workItemService.create(req);
        wi.status = io.casehub.work.runtime.model.WorkItemStatus.COMPLETED;
        wi.persist();

        var ctx = new LifeTaskContext();
        ctx.workItemId = wi.id;
        ctx.domain = LifeDomain.HOUSEHOLD;
        ctx.externalActorId = actorId;
        ctx.persist();
    }

    @Transactional
    void createActiveTaskForActor(UUID actorId) {
        var req = WorkItemCreateRequest.builder()
                .title("Active task")
                .category("household")
                .priority(WorkItemPriority.MEDIUM)
                .candidateGroups("household-member")
                .createdBy("life-system")
                .callerRef("life:task/household-task")
                .scope("casehubio/life/household")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        var wi = workItemService.create(req);

        var ctx = new LifeTaskContext();
        ctx.workItemId = wi.id;
        ctx.domain = LifeDomain.HOUSEHOLD;
        ctx.externalActorId = actorId;
        ctx.persist();
    }
}
