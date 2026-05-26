package io.casehub.life.app;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Household week narrative — makes accountability gaps observable as test state.
 * Gap commentary lives in LAYER-LOG.md, not here.
 * State carries between methods intentionally (@TestMethodOrder).
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ShowcaseScenarioTest {

    // Static fields carry state between ordered test methods — sequential by design, not thread-safe.
    static String bobActorId;
    static String boilerTaskId;
    static String gpEventId;
    static String gpFollowUpTaskId;

    @Test
    @Order(1)
    void contractorTaskCreated() {
        bobActorId = given()
                .contentType("application/json")
                .body("""
                        {"name":"Bob's Plumbing","actorType":"EXTERNAL_HUMAN",
                         "contactMethod":"phone","contactValue":"+44-7700-900100"}
                        """)
                .when().post("/external-actors")
                .then().statusCode(201)
                .body("name", equalTo("Bob's Plumbing"))
                .extract().path("id");

        boilerTaskId = given()
                .contentType("application/json")
                .body("""
                        {"domain":"CONTRACTOR_COORDINATION","title":"Fix boiler",
                         "status":"PENDING","slaHours":48,
                         "externalActorId":"%s",
                         "deadline":"%s"}
                        """.formatted(bobActorId, Instant.now().plus(5, ChronoUnit.DAYS)))
                .when().post("/household-tasks")
                .then().statusCode(201)
                .body("externalActorId", equalTo(bobActorId))
                .body("slaHours", equalTo(48))
                .extract().path("id");

        given()
                .when().get("/external-actors/{id}/tasks", bobActorId)
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].id", equalTo(boilerTaskId));
    }

    @Test
    @Order(2)
    void contractorDeadlinePassedNoEscalation() {
        final String pastDeadline = Instant.now().minus(1, ChronoUnit.DAYS).toString();

        given()
                .contentType("application/json")
                .body("""
                        {"domain":"CONTRACTOR_COORDINATION","title":"Fix boiler",
                         "status":"PENDING","slaHours":48,
                         "externalActorId":"%s","deadline":"%s"}
                        """.formatted(bobActorId, pastDeadline))
                .when().put("/household-tasks/{id}", boilerTaskId)
                .then().statusCode(200);

        final ValidatableResponse taskState = given()
                .when().get("/household-tasks/{id}", boilerTaskId)
                .then().statusCode(200);

        taskState
                .body("status", equalTo("PENDING"))
                .body("deadline", notNullValue());

        // GAP: deadline is in the past, task is still PENDING.
        // No escalation fired. No WorkItem exists. No commitment was tracked.
        // Observable: task state alone — no audit trail, no SLA breach record.
        given()
                .when().get("/external-actors/{id}/tasks", bobActorId)
                .then()
                .statusCode(200)
                .body("[0].status", equalTo("PENDING"));
    }

    @Test
    @Order(3)
    void healthAppointmentCreated() {
        gpEventId = given()
                .contentType("application/json")
                .body("""
                        {"domain":"HEALTH","title":"GP appointment",
                         "occurredAt":"%s"}
                        """.formatted(Instant.now().plus(7, ChronoUnit.DAYS)))
                .when().post("/life-events")
                .then().statusCode(201)
                .body("domain", equalTo("HEALTH"))
                .extract().path("id");

        gpFollowUpTaskId = given()
                .contentType("application/json")
                .body("""
                        {"domain":"HEALTH","title":"GP follow-up call",
                         "status":"PENDING","slaHours":24,
                         "deadline":"%s"}
                        """.formatted(Instant.now().plus(8, ChronoUnit.DAYS)))
                .when().post("/household-tasks")
                .then().statusCode(201)
                .body("slaHours", equalTo(24))
                .extract().path("id");

        given().when().get("/life-events/{id}", gpEventId).then().statusCode(200);
        given().when().get("/household-tasks/{id}", gpFollowUpTaskId).then().statusCode(200);
    }

    @Test
    @Order(4)
    void healthFollowUpSlaBreachedSilently() {
        final String pastDeadline = Instant.now().minus(1, ChronoUnit.HOURS).toString();

        given()
                .contentType("application/json")
                .body("""
                        {"domain":"HEALTH","title":"GP follow-up call",
                         "status":"PENDING","slaHours":24,"deadline":"%s"}
                        """.formatted(pastDeadline))
                .when().put("/household-tasks/{id}", gpFollowUpTaskId)
                .then().statusCode(200);

        given()
                .when().get("/household-tasks/{id}", gpFollowUpTaskId)
                .then()
                .statusCode(200)
                .body("status", equalTo("PENDING"));

        // GAP: slaHours=24, deadline in the past, task still PENDING.
        // No WorkItem was created. No SLA breach record. No notification.
        // The health follow-up is silently overdue.
    }

    @Test
    @Order(5)
    void financialDecisionNoApprovalGate() {
        final String taskId = given()
                .contentType("application/json")
                .body("""
                        {"domain":"FINANCE","title":"Approve boiler replacement quote",
                         "description":"£3,000 decision required — new boiler vs repair",
                         "status":"PENDING"}
                        """)
                .when().post("/household-tasks")
                .then().statusCode(201)
                .body("domain", equalTo("FINANCE"))
                .extract().path("id");

        // GAP: major financial decision stored as a plain task.
        // No COMMAND issued. No oversight channel. No human RESPONSE required before action.
        given()
                .when().get("/household-tasks/{id}", taskId)
                .then()
                .statusCode(200)
                .body("status", equalTo("PENDING"))
                .body("description", containsString("£3,000"));
    }

    @Test
    @Order(6)
    void weekSummaryNoAccountabilityTrail() {
        // All tasks: contractor overdue, health SLA breached, financial pending
        given()
                .when().get("/household-tasks")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(3));

        // PENDING tasks across domains — no SLA report, no escalation log
        given()
                .queryParam("status", "PENDING")
                .when().get("/household-tasks")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(2));

        // GAP: there is no endpoint for SLA compliance, commitment history,
        // or accountability audit. The domain has tasks but no formal trail.
        given()
                .when().get("/external-actors/{id}/tasks", bobActorId)
                .then()
                .statusCode(200)
                .body("[0].status", equalTo("PENDING"));
    }
}
