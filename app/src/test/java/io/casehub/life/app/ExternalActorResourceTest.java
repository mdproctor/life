package io.casehub.life.app;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestSecurity(user = "household-admin", roles = {"household-admin"})
class ExternalActorResourceTest {

    static final String ACTOR_JSON = """
            {
              "name": "Bob's Plumbing-%s",
              "actorType": "EXTERNAL_HUMAN",
              "contactMethod": "phone",
              "contactValue": "+44-7700-900001"
            }
            """;

    @BeforeEach
    @Transactional
    void seedTemplate() {
        LifeTestFixtures.seedStandardTemplates();
    }

    @Test
    void createActor_returnsCreatedWithId() {
        given()
                .contentType("application/json")
                .body(ACTOR_JSON.formatted("create"))
                .when().post("/external-actors")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("name", containsString("Bob's Plumbing"))
                .body("actorType", equalTo("EXTERNAL_HUMAN"))
                .body("contactMethod", equalTo("phone"));
    }

    @Test
    void getActor_returnsActor() {
        String id = given()
                .contentType("application/json")
                .body(ACTOR_JSON.formatted("get"))
                .when().post("/external-actors")
                .then().statusCode(201)
                .extract().path("id");

        given()
                .when().get("/external-actors/{id}", id)
                .then()
                .statusCode(200)
                .body("id", equalTo(id));
    }

    @Test
    void getActor_unknownId_returns404() {
        given()
                .when().get("/external-actors/00000000-0000-0000-0000-000000000000")
                .then()
                .statusCode(404);
    }

    @Test
    void listActors_byActorType_returnsFiltered() {
        given()
                .contentType("application/json")
                .body("""
                        {"name":"AI Agent-%s","actorType":"AI_AGENT","contactMethod":"api","contactValue":"http://agent.local"}
                        """.formatted("list"))
                .when().post("/external-actors")
                .then().statusCode(201);

        given()
                .queryParam("actorType", "AI_AGENT")
                .when().get("/external-actors")
                .then()
                .statusCode(200)
                .body("items.findAll { it.actorType == 'AI_AGENT' }.size()", greaterThanOrEqualTo(1));
    }

    @Test
    void updateActor_returnsUpdated() {
        String id = given()
                .contentType("application/json")
                .body(ACTOR_JSON.formatted("update"))
                .when().post("/external-actors")
                .then().statusCode(201)
                .extract().path("id");

        given()
                .contentType("application/json")
                .body("""
                        {"name":"Updated Plumbing","actorType":"EXTERNAL_HUMAN","contactMethod":"email","contactValue":"bob@plumbing.com"}
                        """)
                .when().put("/external-actors/{id}", id)
                .then()
                .statusCode(200)
                .body("contactMethod", equalTo("email"));
    }

    @Test
    void deleteActor_withNoTasks_returns204() {
        String id = given()
                .contentType("application/json")
                .body(ACTOR_JSON.formatted("delete"))
                .when().post("/external-actors")
                .then().statusCode(201)
                .extract().path("id");

        given()
                .when().delete("/external-actors/{id}", id)
                .then()
                .statusCode(204);
    }

    @Test
    void deleteActor_unknownId_returns404() {
        given()
                .when().delete("/external-actors/00000000-0000-0000-0000-000000000000")
                .then()
                .statusCode(404);
    }

    @Test
    void deleteActor_referencedByTask_returns409() {
        String actorId = given()
                .contentType("application/json")
                .body(ACTOR_JSON.formatted("ref409"))
                .when().post("/external-actors")
                .then().statusCode(201)
                .extract().path("id");

        // Create a life task referencing this actor — establishes LifeTaskContext row.
        given()
                .contentType("application/json")
                .body("""
                        {"templateRef":"household-task","title":"Fix boiler","externalActorId":"%s"}
                        """.formatted(actorId))
                .when().post("/life-tasks")
                .then().statusCode(201);

        // Delete must be blocked — LifeTaskContext referential integrity guard fires.
        given()
                .when().delete("/external-actors/{id}", actorId)
                .then()
                .statusCode(409);
    }

    @Test
    void listActorTasks_returnsTasksForActor() {
        String actorId = given()
                .contentType("application/json")
                .body(ACTOR_JSON.formatted("tasks"))
                .when().post("/external-actors")
                .then().statusCode(201)
                .extract().path("id");

        given()
                .contentType("application/json")
                .body("""
                        {"templateRef":"household-task","title":"Boiler repair","externalActorId":"%s"}
                        """.formatted(actorId))
                .when().post("/life-tasks")
                .then().statusCode(201);

        given()
                .when().get("/external-actors/{id}/tasks", actorId)
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].externalActorId", equalTo(actorId));
    }
}
