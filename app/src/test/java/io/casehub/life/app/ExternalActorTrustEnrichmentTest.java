package io.casehub.life.app;

import io.casehub.ledger.runtime.model.ActorTrustScore;
import io.casehub.ledger.runtime.repository.ActorTrustScoreRepository;
import io.casehub.life.api.LifeActorIds;
import io.casehub.platform.api.identity.ActorType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class ExternalActorTrustEnrichmentTest {

    @Inject ActorTrustScoreRepository trustScoreRepo;

    @Test
    void getExternalActorIncludesTrustProfile() {
        var actorId = createActorAndSeedTrustScore();

        given()
            .when().get("/external-actors/" + actorId)
            .then()
            .statusCode(200)
            .body("trustProfile.globalScore", notNullValue())
            .body("trustProfile.globalScore", equalTo(0.8f))
            .body("trustProfile.dimensionScores", aMapWithSize(0))
            .body("trustProfile.capabilityScores", aMapWithSize(0));
    }

    @Test
    void newActorWithNoTrustDataReturnsEmptyProfile() {
        var actorId = createActor();

        given()
            .when().get("/external-actors/" + actorId)
            .then()
            .statusCode(200)
            .body("trustProfile.globalScore", nullValue())
            .body("trustProfile.dimensionScores", aMapWithSize(0))
            .body("trustProfile.capabilityScores", aMapWithSize(0));
    }

    @Transactional
    UUID createActorAndSeedTrustScore() {
        UUID id = createActor();
        String lifeActorId = LifeActorIds.of(id);
        trustScoreRepo.upsert(lifeActorId, ActorTrustScore.ScoreType.GLOBAL,
            null, null, ActorType.HUMAN, 0.8,
            10, 1, 8.0, 2.0, 8, 2, Instant.now());
        return id;
    }

    UUID createActor() {
        return UUID.fromString(
            given()
                .contentType("application/json")
                .body("""
                    {
                      "name": "Test Contractor",
                      "actorType": "EXTERNAL_HUMAN",
                      "contactMethod": "email",
                      "contactValue": "test@example.com"
                    }
                    """)
                .when().post("/external-actors")
                .then().statusCode(201)
                .extract().path("id"));
    }
}
