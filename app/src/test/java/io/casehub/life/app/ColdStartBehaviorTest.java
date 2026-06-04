package io.casehub.life.app;

import io.casehub.api.spi.routing.TrustRoutingPolicy;
import io.casehub.api.spi.routing.TrustRoutingPolicyProvider;
import io.casehub.ledger.runtime.service.TrustGateService;
import io.casehub.life.api.LifeActorIds;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class ColdStartBehaviorTest {

    @Inject
    TrustRoutingPolicyProvider policyProvider;

    @Inject
    TrustGateService trustGateService;

    @BeforeEach
    @Transactional
    void seedTemplate() {
        LifeTestFixtures.seedStandardTemplates();
    }

    @Test
    void policiesAvailableWithNoTrustData() {
        var policy = policyProvider.forCapability("book-appointment");
        assertThat(policy).isNotNull();
        assertThat(policy.threshold()).isEqualTo(0.75);
    }

    @Test
    void unknownCapabilityReturnsDefaultPolicy() {
        var policy = policyProvider.forCapability("nonexistent-capability");
        assertThat(policy).isEqualTo(TrustRoutingPolicy.DEFAULT);
    }

    @Test
    void trustGateReturnsEmptyForUnknownActor() {
        String unknownActorId = LifeActorIds.of(UUID.randomUUID());
        assertThat(trustGateService.currentScore(unknownActorId)).isEmpty();
    }

    @Test
    void trustGateDimensionScoresEmptyForUnknownActor() {
        String unknownActorId = LifeActorIds.of(UUID.randomUUID());
        assertThat(trustGateService.dimensionScores(unknownActorId)).isEmpty();
    }

    @Test
    void restEndpointReturnsColdStartTrustProfile() {
        String actorId = given()
                .contentType("application/json")
                .body("""
                        {"name":"Cold Start Actor","actorType":"EXTERNAL_HUMAN","contactMethod":"phone","contactValue":"+44-7700-900099"}
                        """)
                .when().post("/external-actors")
                .then().statusCode(201)
                .extract().path("id");

        given()
                .when().get("/external-actors/{id}", actorId)
                .then()
                .statusCode(200)
                .body("trustProfile.globalScore", nullValue())
                .body("trustProfile.dimensionScores", anEmptyMap())
                .body("trustProfile.capabilityScores", anEmptyMap());
    }
}
