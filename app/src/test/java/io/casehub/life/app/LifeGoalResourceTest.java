package io.casehub.life.app;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class LifeGoalResourceTest {

    @Test
    void createGoal_returnsCreatedWithId() {
        given()
                .contentType("application/json")
                .body("""
                        {"domain":"HEALTH","title":"Run 5k weekly","status":"ACTIVE"}
                        """)
                .when().post("/life-goals")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("domain", equalTo("HEALTH"))
                .body("status", equalTo("ACTIVE"));
    }

    @Test
    void getGoal_returnsGoal() {
        String id = given()
                .contentType("application/json")
                .body("""
                        {"domain":"FINANCE","title":"Save emergency fund","status":"ACTIVE"}
                        """)
                .when().post("/life-goals")
                .then().statusCode(201)
                .extract().path("id");

        given()
                .when().get("/life-goals/{id}", id)
                .then()
                .statusCode(200)
                .body("id", equalTo(id));
    }

    @Test
    void getGoal_unknownId_returns404() {
        given()
                .when().get("/life-goals/00000000-0000-0000-0000-000000000000")
                .then()
                .statusCode(404);
    }

    @Test
    void listGoals_byDomain_returnsFiltered() {
        given()
                .contentType("application/json")
                .body("""
                        {"domain":"TRAVEL","title":"Visit Japan","status":"ACTIVE"}
                        """)
                .when().post("/life-goals")
                .then().statusCode(201);

        given()
                .queryParam("domain", "TRAVEL")
                .when().get("/life-goals")
                .then()
                .statusCode(200)
                .body("findAll { it.domain == 'TRAVEL' }.size()", greaterThanOrEqualTo(1));
    }

    @Test
    void listGoals_byStatus_returnsFiltered() {
        given()
                .contentType("application/json")
                .body("""
                        {"domain":"HOUSEHOLD","title":"Declutter garage","status":"PAUSED"}
                        """)
                .when().post("/life-goals")
                .then().statusCode(201);

        given()
                .queryParam("status", "PAUSED")
                .when().get("/life-goals")
                .then()
                .statusCode(200)
                .body("findAll { it.status == 'PAUSED' }.size()", greaterThanOrEqualTo(1));
    }

    @Test
    void updateGoal_returnsUpdated() {
        String id = given()
                .contentType("application/json")
                .body("""
                        {"domain":"LEGAL","title":"Update will","status":"ACTIVE"}
                        """)
                .when().post("/life-goals")
                .then().statusCode(201)
                .extract().path("id");

        given()
                .contentType("application/json")
                .body("""
                        {"domain":"LEGAL","title":"Update will","status":"COMPLETED"}
                        """)
                .when().put("/life-goals/{id}", id)
                .then()
                .statusCode(200)
                .body("status", equalTo("COMPLETED"));
    }

    @Test
    void deleteGoal_returns204() {
        String id = given()
                .contentType("application/json")
                .body("""
                        {"domain":"FAMILY_SCHEDULING","title":"Plan reunion","status":"ACTIVE"}
                        """)
                .when().post("/life-goals")
                .then().statusCode(201)
                .extract().path("id");

        given()
                .when().delete("/life-goals/{id}", id)
                .then()
                .statusCode(204);
    }

    @Test
    void deleteGoal_unknownId_returns404() {
        given()
                .when().delete("/life-goals/00000000-0000-0000-0000-000000000000")
                .then()
                .statusCode(404);
    }
}
