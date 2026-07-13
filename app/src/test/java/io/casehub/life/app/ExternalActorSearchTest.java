package io.casehub.life.app;

import io.casehub.life.api.LifeActorType;
import io.casehub.life.app.entity.ExternalActor;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestSecurity(user = "household-admin", roles = {"household-admin"})
class ExternalActorSearchTest {

    @BeforeEach
    @Transactional
    void seedActors() {
        ExternalActor.deleteAll();
        createActor("Alice Plumbing", LifeActorType.EXTERNAL_HUMAN, "phone", "07700900001", false);
        createActor("Bob Electric", LifeActorType.EXTERNAL_HUMAN, "email", "bob@electric.co.uk", false);
        createActor("Dr Carol Smith", LifeActorType.HOUSEHOLD_PRINCIPAL, "email", "carol@nhs.uk", false);
        createActor("Erased Corp", LifeActorType.AI_AGENT, "email", "erased@corp.com", true);
    }

    @Test
    void search_noFilters_returnsPagedResponse() {
        given().contentType(ContentType.JSON)
                .when().get("/external-actors")
                .then().statusCode(200)
                .body("items", hasSize(4))
                .body("page", equalTo(0))
                .body("size", equalTo(20))
                .body("totalCount", equalTo(4));
    }

    @Test
    void search_byName_caseInsensitive() {
        given().contentType(ContentType.JSON)
                .queryParam("name", "alice")
                .when().get("/external-actors")
                .then().statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].name", equalTo("Alice Plumbing"));
    }

    @Test
    void search_byActorType() {
        given().contentType(ContentType.JSON)
                .queryParam("actorType", "EXTERNAL_HUMAN")
                .when().get("/external-actors")
                .then().statusCode(200)
                .body("items", hasSize(2))
                .body("totalCount", equalTo(2));
    }

    @Test
    void search_byContactMethod() {
        given().contentType(ContentType.JSON)
                .queryParam("contactMethod", "email")
                .when().get("/external-actors")
                .then().statusCode(200)
                .body("items", hasSize(3));
    }

    @Test
    void search_erasedOnly() {
        given().contentType(ContentType.JSON)
                .queryParam("erasedOnly", true)
                .when().get("/external-actors")
                .then().statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].name", equalTo("[ERASED]"));
    }

    @Test
    void search_pagination_page0Size2() {
        given().contentType(ContentType.JSON)
                .queryParam("page", 0)
                .queryParam("size", 2)
                .when().get("/external-actors")
                .then().statusCode(200)
                .body("items", hasSize(2))
                .body("totalCount", equalTo(4))
                .body("page", equalTo(0))
                .body("size", equalTo(2));
    }

    @Test
    void search_pagination_page1Size2() {
        given().contentType(ContentType.JSON)
                .queryParam("page", 1)
                .queryParam("size", 2)
                .when().get("/external-actors")
                .then().statusCode(200)
                .body("items", hasSize(2))
                .body("totalCount", equalTo(4));
    }

    @Test
    void search_pagination_beyondLastPage_returnsEmpty() {
        given().contentType(ContentType.JSON)
                .queryParam("page", 10)
                .queryParam("size", 20)
                .when().get("/external-actors")
                .then().statusCode(200)
                .body("items", hasSize(0))
                .body("totalCount", equalTo(4));
    }

    @Test
    void search_combinedFilters() {
        given().contentType(ContentType.JSON)
                .queryParam("actorType", "EXTERNAL_HUMAN")
                .queryParam("contactMethod", "email")
                .when().get("/external-actors")
                .then().statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].name", equalTo("Bob Electric"));
    }

    @Test
    void search_sizeCappedAt100() {
        given().contentType(ContentType.JSON)
                .queryParam("size", 500)
                .when().get("/external-actors")
                .then().statusCode(200)
                .body("size", equalTo(100));
    }

    private void createActor(String name, LifeActorType type, String method, String value, boolean erased) {
        ExternalActor a = new ExternalActor();
        a.name = erased ? "[ERASED]" : name;
        a.actorType = type;
        a.contactMethod = method;
        a.contactValue = value;
        if (erased) a.gdprErasedAt = java.time.Instant.now();
        a.persist();
    }
}
