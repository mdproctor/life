package io.casehub.life.app;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class LifeEventResourceTest {

    @Test
    void createEvent_returnsCreatedWithId() {
        given()
                .contentType("application/json")
                .body("""
                        {"domain":"HEALTH","title":"GP appointment","occurredAt":"2026-06-10T09:00:00Z"}
                        """)
                .when().post("/life-events")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("domain", equalTo("HEALTH"))
                .body("title", equalTo("GP appointment"));
    }

    @Test
    void getEvent_returnsEvent() {
        String id = given()
                .contentType("application/json")
                .body("""
                        {"domain":"LEGAL","title":"Solicitor meeting","occurredAt":"2026-06-15T14:00:00Z"}
                        """)
                .when().post("/life-events")
                .then().statusCode(201)
                .extract().path("id");

        given()
                .when().get("/life-events/{id}", id)
                .then()
                .statusCode(200)
                .body("id", equalTo(id));
    }

    @Test
    void getEvent_unknownId_returns404() {
        given()
                .when().get("/life-events/00000000-0000-0000-0000-000000000000")
                .then()
                .statusCode(404);
    }

    @Test
    void listEvents_byDomain_returnsFiltered() {
        given()
                .contentType("application/json")
                .body("""
                        {"domain":"CONTRACTOR_COORDINATION","title":"Plumber visit","occurredAt":"2026-06-20T10:00:00Z"}
                        """)
                .when().post("/life-events")
                .then().statusCode(201);

        given()
                .queryParam("domain", "CONTRACTOR_COORDINATION")
                .when().get("/life-events")
                .then()
                .statusCode(200)
                .body("findAll { it.domain == 'CONTRACTOR_COORDINATION' }.size()", greaterThanOrEqualTo(1));
    }

    @Test
    void deleteEvent_returns204() {
        String id = given()
                .contentType("application/json")
                .body("""
                        {"domain":"HOUSEHOLD","title":"Boiler service","occurredAt":"2026-07-01T08:00:00Z"}
                        """)
                .when().post("/life-events")
                .then().statusCode(201)
                .extract().path("id");

        given()
                .when().delete("/life-events/{id}", id)
                .then()
                .statusCode(204);
    }

    @Test
    void deleteEvent_unknownId_returns404() {
        given()
                .when().delete("/life-events/00000000-0000-0000-0000-000000000000")
                .then()
                .statusCode(404);
    }

    @Test
    void putEvent_returns405_eventIsImmutable() {
        String id = given()
                .contentType("application/json")
                .body("""
                        {"domain":"TRAVEL","title":"Flight booking","occurredAt":"2026-08-01T06:00:00Z"}
                        """)
                .when().post("/life-events")
                .then().statusCode(201)
                .extract().path("id");

        // No PUT endpoint exists — LifeEvent is immutable after creation
        given()
                .contentType("application/json")
                .body("""
                        {"domain":"TRAVEL","title":"Changed","occurredAt":"2026-08-01T06:00:00Z"}
                        """)
                .when().put("/life-events/{id}", id)
                .then()
                .statusCode(405);
    }
}
