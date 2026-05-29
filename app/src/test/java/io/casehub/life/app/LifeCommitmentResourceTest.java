package io.casehub.life.app;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class LifeCommitmentResourceTest {

    @BeforeEach
    @Transactional
    void seedTemplates() {
        LifeTestFixtures.seedStandardTemplates();
        LifeTestFixtures.seedEscalationTemplate();
    }

    // --- POST /life-tasks/{id}/commit ---

    @Test
    void commit_delegation_returns201WithDelegationMode() {
        final String taskId = createTask("household-task", "Pick up kids");
        final Instant deadline = Instant.now().plus(3, ChronoUnit.HOURS);

        given()
                .contentType("application/json")
                .body("""
                        {"delegateTo":"alice","deadline":"%s"}
                        """.formatted(deadline))
                .when().post("/life-tasks/{id}/commit", taskId)
                .then()
                .statusCode(201)
                .body("mode", equalTo("DELEGATION"))
                .body("status", equalTo("PENDING_RESPONSE"))
                .body("recordId", notNullValue())
                .body("correlationId", notNullValue());
    }

    @Test
    void commit_contractor_returns201WithContractorMode() {
        final String actorId = createActor("Bob's Plumbing");
        final String taskId = createTask("contractor-coordination", "Fix boiler", actorId);
        final Instant deadline = Instant.now().plus(48, ChronoUnit.HOURS);

        given()
                .contentType("application/json")
                .body("""
                        {"externalActorId":"%s","deadline":"%s"}
                        """.formatted(actorId, deadline))
                .when().post("/life-tasks/{id}/commit", taskId)
                .then()
                .statusCode(201)
                .body("mode", equalTo("CONTRACTOR"))
                .body("status", equalTo("PENDING_RESPONSE"));
    }

    @Test
    void commit_neitherDelegateToNorExternalActor_returns422() {
        final String taskId = createTask("household-task", "Some task");

        given()
                .contentType("application/json")
                .body("""
                        {"deadline":"%s"}
                        """.formatted(Instant.now().plus(1, ChronoUnit.HOURS)))
                .when().post("/life-tasks/{id}/commit", taskId)
                .then()
                .statusCode(422);
    }

    @Test
    void commit_bothDelegateToAndExternalActor_returns422() {
        final String actorId = createActor("Jane Plumber");
        final String taskId = createTask("household-task", "Ambiguous task");

        given()
                .contentType("application/json")
                .body("""
                        {"delegateTo":"alice","externalActorId":"%s","deadline":"%s"}
                        """.formatted(actorId, Instant.now().plus(1, ChronoUnit.HOURS)))
                .when().post("/life-tasks/{id}/commit", taskId)
                .then()
                .statusCode(422);
    }

    @Test
    void commit_withoutDeadline_returns422() {
        final String taskId = createTask("household-task", "No deadline task");

        given()
                .contentType("application/json")
                .body("""
                        {"delegateTo":"alice"}
                        """)
                .when().post("/life-tasks/{id}/commit", taskId)
                .then()
                .statusCode(422);
    }

    @Test
    void commit_unknownTask_returns404() {
        given()
                .contentType("application/json")
                .body("""
                        {"delegateTo":"alice","deadline":"%s"}
                        """.formatted(Instant.now().plus(1, ChronoUnit.HOURS)))
                .when().post("/life-tasks/{id}/commit", UUID.randomUUID())
                .then()
                .statusCode(404);
    }

    @Test
    void commit_unknownExternalActor_returns422() {
        final String taskId = createTask("contractor-coordination", "Ghost contractor");

        given()
                .contentType("application/json")
                .body("""
                        {"externalActorId":"%s","deadline":"%s"}
                        """.formatted(UUID.randomUUID(), Instant.now().plus(1, ChronoUnit.HOURS)))
                .when().post("/life-tasks/{id}/commit", taskId)
                .then()
                .statusCode(422);
    }

    @Test
    void commit_duplicate_returns409() {
        final String taskId = createTask("household-task", "Pickup task");
        final String body = """
                {"delegateTo":"alice","deadline":"%s"}
                """.formatted(Instant.now().plus(2, ChronoUnit.HOURS));

        given().contentType("application/json").body(body)
                .when().post("/life-tasks/{id}/commit", taskId)
                .then().statusCode(201);

        // Second commit on same task → 409
        given().contentType("application/json").body(body)
                .when().post("/life-tasks/{id}/commit", taskId)
                .then().statusCode(409);
    }

    @Test
    void getTask_afterCommit_includesCommitmentMode() {
        final String taskId = createTask("household-task", "Chore with commitment");

        given()
                .contentType("application/json")
                .body("""
                        {"delegateTo":"bob","deadline":"%s"}
                        """.formatted(Instant.now().plus(3, ChronoUnit.HOURS)))
                .when().post("/life-tasks/{id}/commit", taskId)
                .then().statusCode(201);

        given()
                .when().get("/life-tasks/{id}", taskId)
                .then()
                .statusCode(200)
                .body("commitmentMode", equalTo("DELEGATION"))
                .body("commitmentStatus", equalTo("PENDING_RESPONSE"));
    }

    // --- POST /life-oversight-gates ---

    @Test
    void oversightGate_returns201WithOversightMode() {
        given()
                .contentType("application/json")
                .body("""
                        {
                          "deadline":"%s",
                          "pendingTask":{"templateRef":"household-task","title":"Buy new sofa"}
                        }
                        """.formatted(Instant.now().plus(24, ChronoUnit.HOURS)))
                .when().post("/life-oversight-gates")
                .then()
                .statusCode(201)
                .body("mode", equalTo("OVERSIGHT"))
                .body("status", equalTo("PENDING_RESPONSE"))
                .body("recordId", notNullValue());
    }

    @Test
    void oversightGate_duplicate_returns409() {
        final String body = """
                {
                  "deadline":"%s",
                  "pendingTask":{"templateRef":"household-task","title":"Duplicate gate task"}
                }
                """.formatted(Instant.now().plus(24, ChronoUnit.HOURS));

        given().contentType("application/json").body(body)
                .when().post("/life-oversight-gates")
                .then().statusCode(201);

        given().contentType("application/json").body(body)
                .when().post("/life-oversight-gates")
                .then().statusCode(409);
    }

    // --- Helpers ---

    private String createTask(final String templateRef, final String title) {
        return given()
                .contentType("application/json")
                .body("""
                        {"templateRef":"%s","title":"%s"}
                        """.formatted(templateRef, title))
                .when().post("/life-tasks")
                .then().statusCode(201)
                .extract().path("workItemId");
    }

    private String createTask(final String templateRef, final String title, final String actorId) {
        return given()
                .contentType("application/json")
                .body("""
                        {"templateRef":"%s","title":"%s","externalActorId":"%s"}
                        """.formatted(templateRef, title, actorId))
                .when().post("/life-tasks")
                .then().statusCode(201)
                .extract().path("workItemId");
    }

    private String createActor(final String name) {
        return given()
                .contentType("application/json")
                .body("""
                        {"name":"%s","actorType":"EXTERNAL_HUMAN","contactMethod":"phone","contactValue":"+44-7700-900999"}
                        """.formatted(name))
                .when().post("/external-actors")
                .then().statusCode(201)
                .extract().path("id");
    }
}
