package io.casehub.life.app.resource;

import io.casehub.life.api.HouseholdGroups;
import io.casehub.life.app.LifeTestFixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;

import static io.restassured.RestAssured.given;

@QuarkusTest
@TestSecurity(user = "admin", roles = {HouseholdGroups.ADMIN})
class LifeEventSseResourceTest {

    @BeforeEach
    @Transactional
    void setup() {
        LifeTestFixtures.seedStandardTemplates();
        LifeTestFixtures.seedEscalationTemplate();
    }

    @Test
    void inboxEndpointConnects() {
        given()
                .config(RestAssuredConfig.config()
                                         .httpClient(HttpClientConfig.httpClientConfig()
                                                                     .setParam("http.socket.timeout", 1000)))
                .when().get("/events/inbox")
                .then()
                .statusCode(200);
    }

    @Test
    void casesEndpointConnects() {
        given()
                .config(RestAssuredConfig.config()
                                         .httpClient(HttpClientConfig.httpClientConfig()
                                                                     .setParam("http.socket.timeout", 1000)))
                .when().get("/events/cases")
                .then()
                .statusCode(200);
    }

    @Test
    @TestSecurity(user = "junior", roles = {HouseholdGroups.JUNIOR})
    void juniorCannotAccessCases() {
        given()
                .config(RestAssuredConfig.config()
                                         .httpClient(HttpClientConfig.httpClientConfig()
                                                                     .setParam("http.socket.timeout", 1000)))
                .when().get("/events/cases")
                .then()
                .statusCode(403);
    }
}
