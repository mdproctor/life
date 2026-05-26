package io.casehub.life.app;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class HouseholdTaskResourceTest {

    static final String TASK_JSON = """
            {
              "domain": "HOUSEHOLD",
              "title": "Vacuum living room-%s",
              "status": "PENDING"
            }
            """;

    @Test
    void createTask_returnsCreatedWithId() {
        given()
                .contentType("application/json")
                .body(TASK_JSON.formatted("create"))
                .when().post("/household-tasks")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("domain", equalTo("HOUSEHOLD"))
                .body("status", equalTo("PENDING"));
    }

    @Test
    void getTask_returnsTask() {
        String id = given()
                .contentType("application/json")
                .body(TASK_JSON.formatted("get"))
                .when().post("/household-tasks")
                .then().statusCode(201)
                .extract().path("id");

        given()
                .when().get("/household-tasks/{id}", id)
                .then()
                .statusCode(200)
                .body("id", equalTo(id));
    }

    @Test
    void getTask_unknownId_returns404() {
        given()
                .when().get("/household-tasks/00000000-0000-0000-0000-000000000000")
                .then()
                .statusCode(404);
    }

    @Test
    void listTasks_byDomain_returnsFiltered() {
        given()
                .contentType("application/json")
                .body("""
                        {"domain":"HEALTH","title":"GP appointment-%s","status":"PENDING"}
                        """.formatted("filter"))
                .when().post("/household-tasks")
                .then().statusCode(201);

        given()
                .queryParam("domain", "HEALTH")
                .when().get("/household-tasks")
                .then()
                .statusCode(200)
                .body("findAll { it.domain == 'HEALTH' }.size()", greaterThanOrEqualTo(1));
    }

    @Test
    void listTasks_byStatus_returnsFiltered() {
        given()
                .contentType("application/json")
                .body("""
                        {"domain":"FINANCE","title":"Budget review-%s","status":"IN_PROGRESS"}
                        """.formatted("status"))
                .when().post("/household-tasks")
                .then().statusCode(201);

        given()
                .queryParam("status", "IN_PROGRESS")
                .when().get("/household-tasks")
                .then()
                .statusCode(200)
                .body("findAll { it.status == 'IN_PROGRESS' }.size()", greaterThanOrEqualTo(1));
    }

    @Test
    void listTasks_byAssignedTo_returnsFiltered() {
        given()
                .contentType("application/json")
                .body("""
                        {"domain":"HOUSEHOLD","title":"Shopping-%s","status":"PENDING","assignedTo":"alice-%s"}
                        """.formatted("assigned", "assigned"))
                .when().post("/household-tasks")
                .then().statusCode(201);

        given()
                .queryParam("assignedTo", "alice-assigned")
                .when().get("/household-tasks")
                .then()
                .statusCode(200)
                .body("findAll { it.assignedTo == 'alice-assigned' }.size()", greaterThanOrEqualTo(1));
    }

    @Test
    void listTasks_byExternalActorId_returnsFiltered() {
        String actorId = given()
                .contentType("application/json")
                .body("""
                        {"name":"Test Plumber-%s","actorType":"EXTERNAL_HUMAN","contactMethod":"phone","contactValue":"+44-0000-000001"}
                        """.formatted("filter"))
                .when().post("/external-actors")
                .then().statusCode(201)
                .extract().path("id");

        given()
                .contentType("application/json")
                .body("""
                        {"domain":"CONTRACTOR_COORDINATION","title":"Fix tap-%s","status":"PENDING","externalActorId":"%s"}
                        """.formatted("filter", actorId))
                .when().post("/household-tasks")
                .then().statusCode(201);

        given()
                .queryParam("externalActorId", actorId)
                .when().get("/household-tasks")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(1));
    }

    @Test
    void updateTask_returnsUpdated() {
        String id = given()
                .contentType("application/json")
                .body(TASK_JSON.formatted("update"))
                .when().post("/household-tasks")
                .then().statusCode(201)
                .extract().path("id");

        given()
                .contentType("application/json")
                .body("""
                        {"domain":"HOUSEHOLD","title":"Updated task","status":"COMPLETED"}
                        """)
                .when().put("/household-tasks/{id}", id)
                .then()
                .statusCode(200)
                .body("status", equalTo("COMPLETED"));
    }

    @Test
    void updateTask_unknownId_returns404() {
        given()
                .contentType("application/json")
                .body("""
                        {"domain":"HOUSEHOLD","title":"Ghost","status":"PENDING"}
                        """)
                .when().put("/household-tasks/00000000-0000-0000-0000-000000000000")
                .then()
                .statusCode(404);
    }

    @Test
    void deleteTask_returns204() {
        String id = given()
                .contentType("application/json")
                .body(TASK_JSON.formatted("delete"))
                .when().post("/household-tasks")
                .then().statusCode(201)
                .extract().path("id");

        given()
                .when().delete("/household-tasks/{id}", id)
                .then()
                .statusCode(204);
    }

    @Test
    void deleteTask_unknownId_returns404() {
        given()
                .when().delete("/household-tasks/00000000-0000-0000-0000-000000000000")
                .then()
                .statusCode(404);
    }

    @Test
    void createTask_withSlaHoursAndExternalActor_persistsBoth() {
        String actorId = given()
                .contentType("application/json")
                .body("""
                        {"name":"SLA Plumber","actorType":"EXTERNAL_HUMAN","contactMethod":"phone","contactValue":"+44-0000-000002"}
                        """)
                .when().post("/external-actors")
                .then().statusCode(201)
                .extract().path("id");

        given()
                .contentType("application/json")
                .body("""
                        {"domain":"CONTRACTOR_COORDINATION","title":"Boiler service","status":"PENDING",
                         "slaHours":48,"externalActorId":"%s"}
                        """.formatted(actorId))
                .when().post("/household-tasks")
                .then()
                .statusCode(201)
                .body("slaHours", equalTo(48))
                .body("externalActorId", equalTo(actorId));
    }
}
