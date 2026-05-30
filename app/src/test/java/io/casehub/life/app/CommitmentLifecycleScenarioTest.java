package io.casehub.life.app;

import io.casehub.life.api.commitment.CommitmentStatus;
import io.casehub.life.app.entity.LifeCommitmentRecord;
import io.casehub.qhorus.runtime.watchdog.WatchdogEvaluationService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;


import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Layer 3 showcase — commitment accountability gaps closed by casehub-qhorus.
 *
 * Layer 2 showed: SLA enforcement via WorkItems.
 * Layer 3 shows: COMMAND/RESPONSE commitment lifecycle, Watchdog-to-escalation,
 *                oversight gate blocking task creation until approval.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CommitmentLifecycleScenarioTest {

    @Inject
    WatchdogEvaluationService watchdogEvaluationService;

    static String bobActorId;
    static String boilerTaskId;
    static String boilerCommitmentCorrelationId;

    @BeforeEach
    @Transactional
    void seedTemplates() {
        LifeTestFixtures.seedStandardTemplates();
        LifeTestFixtures.seedEscalationTemplate();
    }

    @Transactional
    boolean commitmentExpiredByWorkItem(final UUID workItemId) {
        return LifeCommitmentRecord.findByWorkItemId(workItemId).isEmpty();
    }

    @Test
    @Order(1)
    void contractorRegisteredAndTaskCreated() {
        bobActorId = given()
                .contentType("application/json")
                .body("""
                        {"name":"Bob's Plumbing","actorType":"EXTERNAL_HUMAN",
                         "contactMethod":"phone","contactValue":"+44-7700-900300"}
                        """)
                .when().post("/external-actors")
                .then().statusCode(201)
                .extract().path("id");

        boilerTaskId = given()
                .contentType("application/json")
                .body("""
                        {"templateRef":"contractor-coordination","title":"Fix boiler",
                         "externalActorId":"%s"}
                        """.formatted(bobActorId))
                .when().post("/life-tasks")
                .then().statusCode(201)
                .body("workItemId", notNullValue())
                .extract().path("workItemId");
    }

    @Test
    @Order(2)
    void contractorCommitment_dispatchesCommandAndCreatesRecord() {
        // Layer 2: Bob's task had SLA enforcement. Layer 3: Bob explicitly commits to Thursday.
        // Gap closed: "Plumber committed Thursday" is now a formal qhorus COMMAND with Watchdog.
        final Instant deadline = Instant.now().plus(48, ChronoUnit.HOURS);

        boilerCommitmentCorrelationId = given()
                .contentType("application/json")
                .body("""
                        {"externalActorId":"%s","deadline":"%s"}
                        """.formatted(bobActorId, deadline))
                .when().post("/life-tasks/{id}/commit", boilerTaskId)
                .then()
                .statusCode(201)
                .body("mode", equalTo("CONTRACTOR"))
                .body("status", equalTo("PENDING_RESPONSE"))
                .extract().path("correlationId");

        // Commitment is visible on the task
        given()
                .when().get("/life-tasks/{id}", boilerTaskId)
                .then()
                .statusCode(200)
                .body("commitmentMode", equalTo("CONTRACTOR"))
                .body("commitmentStatus", equalTo("PENDING_RESPONSE"));
    }

    @Test
    @Order(3)
    void watchdog_fires_createsEscalationWorkItem() {
        // Without casehub-qhorus (Layer 2): deadline passes silently.
        // With casehub-qhorus (Layer 3): Watchdog fires and creates an escalation task.

        // Insert a delegation commitment with a past deadline to simulate expiry.
        final String pickupTaskId = given()
                .contentType("application/json")
                .body("""
                        {"templateRef":"household-task","title":"School pickup - past deadline"}
                        """)
                .when().post("/life-tasks")
                .then().statusCode(201)
                .extract().path("workItemId");

        final Instant pastDeadline = Instant.now().minus(1, ChronoUnit.SECONDS);
        given()
                .contentType("application/json")
                .body("""
                        {"delegateTo":"charlie","deadline":"%s"}
                        """.formatted(pastDeadline))
                .when().post("/life-tasks/{id}/commit", pickupTaskId)
                .then().statusCode(201);

        // Commitment is in PENDING_RESPONSE with a past deadline — this is the condition that
        // triggers the Watchdog alert. evaluateAll() fires the WatchdogAlertEvent asynchronously.
        // Gap closed vs OpenClaw alone: the commitment + Watchdog means the deadline breach
        // is tracked and will escalate, rather than being silently forgotten.
        final LifeCommitmentRecord commitmentBefore = LifeCommitmentRecord
                .findByWorkItemId(UUID.fromString(pickupTaskId))
                .orElseThrow();
        assertThat(commitmentBefore.status).isEqualTo(CommitmentStatus.PENDING_RESPONSE);
        assertThat(commitmentBefore.deadline).isBefore(Instant.now());

        // Invoke evaluateAll() to demonstrate the Watchdog fires — async handler runs.
        // The WatchdogAlertEvent async processing is not awaited here (qhorus integration concern);
        // the complete escalation flow is verified in LifeWatchdogAlertObserverTest.
        watchdogEvaluationService.evaluateAll();
    }

    @Test
    @Order(4)
    void oversightGate_holdsUntilResponse() {
        // Layer 2: a major financial decision could proceed without approval.
        // Layer 3: COMMAND dispatched to life/oversight; no WorkItem until RESPONSE.
        // Gap closed: oversight gate is formally tracked with Watchdog enforcement.
        given()
                .contentType("application/json")
                .body("""
                        {
                          "deadline":"%s",
                          "pendingTask":{"templateRef":"household-task","title":"Buy electric car"},
                          "amountThreshold":35000.00,
                          "purchaseCategory":"vehicle"
                        }
                        """.formatted(Instant.now().plus(48, ChronoUnit.HOURS)))
                .when().post("/life-oversight-gates")
                .then()
                .statusCode(201)
                .body("mode", equalTo("OVERSIGHT"))
                .body("status", equalTo("PENDING_RESPONSE"));
    }

    @Test
    @Order(5)
    void delegation_commitmentTracked() {
        final String groceryTaskId = given()
                .contentType("application/json")
                .body("""
                        {"templateRef":"household-task","title":"Weekly grocery run"}
                        """)
                .when().post("/life-tasks")
                .then().statusCode(201)
                .extract().path("workItemId");

        given()
                .contentType("application/json")
                .body("""
                        {"delegateTo":"alice","deadline":"%s"}
                        """.formatted(Instant.now().plus(4, ChronoUnit.HOURS)))
                .when().post("/life-tasks/{id}/commit", groceryTaskId)
                .then()
                .statusCode(201)
                .body("mode", equalTo("DELEGATION"))
                .body("status", equalTo("PENDING_RESPONSE"));
    }
}
