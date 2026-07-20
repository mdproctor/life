package io.casehub.life.app;

import io.casehub.ledger.api.model.ScoreType;
import io.casehub.ledger.runtime.model.ActorTrustScore;
import io.casehub.life.api.LifeActorIds;
import io.casehub.life.api.LifeActorType;
import io.casehub.life.api.LifeCaseStatus;
import io.casehub.life.api.LifeCaseType;
import io.casehub.life.app.entity.ExternalActor;
import io.casehub.life.app.entity.LifeCaseTracker;
import io.casehub.life.app.entity.LifeTaskContext;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.runtime.model.WorkItem;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
@TestSecurity(user = "household-admin", roles = {"household-admin"})
class LifeAnalyticsTest {

    @Inject
    @io.quarkus.hibernate.orm.PersistenceUnit("qhorus")
    EntityManager qhorusEm;

    @BeforeEach
    @Transactional
    void seed() {
        LifeCaseTracker.deleteAll();
        LifeTaskContext.deleteAll();
        WorkItem.deleteAll();
        ExternalActor.deleteAll();
        LifeTestFixtures.seedStandardTemplates();
        qhorusEm.createQuery("DELETE FROM ActorTrustScore").executeUpdate();
    }

    // ── Case Statistics ──

    @Test
    void caseStatistics_groupsByCaseType() {
        seedTracker("travel-plan", LifeCaseStatus.COMPLETED, 48);
        seedTracker("travel-plan", LifeCaseStatus.COMPLETED, 72);
        seedTracker("travel-plan", LifeCaseStatus.ACTIVE, null);
        seedTracker("home-maintenance", LifeCaseStatus.COMPLETED, 24);

        given().contentType(ContentType.JSON)
                .when().get("/analytics/cases")
                .then().statusCode(200)
                .body("entries", hasSize(2))
                .body("entries.find { it.caseType == 'travel-plan' }.total", equalTo(3))
                .body("entries.find { it.caseType == 'travel-plan' }.completed", equalTo(2))
                .body("entries.find { it.caseType == 'travel-plan' }.active", equalTo(1));
    }

    @Test
    void caseStatistics_filterByCaseType() {
        seedTracker("travel-plan", LifeCaseStatus.COMPLETED, 48);
        seedTracker("home-maintenance", LifeCaseStatus.COMPLETED, 24);

        given().contentType(ContentType.JSON)
                .queryParam("caseType", "travel-plan")
                .when().get("/analytics/cases")
                .then().statusCode(200)
                .body("entries", hasSize(1))
                .body("entries[0].caseType", equalTo("travel-plan"));
    }

    @Test
    void caseStatistics_emptyResult() {
        given().contentType(ContentType.JSON)
                .when().get("/analytics/cases")
                .then().statusCode(200)
                .body("entries", hasSize(0));
    }

    @Test
    void caseStatistics_resolutionTime() {
        seedTracker("travel-plan", LifeCaseStatus.COMPLETED, 48);
        seedTracker("travel-plan", LifeCaseStatus.COMPLETED, 72);

        given().contentType(ContentType.JSON)
                .when().get("/analytics/cases")
                .then().statusCode(200)
                .body("entries[0].avgResolutionHours", notNullValue())
                .body("entries[0].p50ResolutionHours", notNullValue())
                .body("entries[0].p95ResolutionHours", notNullValue());
    }

    @Test
    void caseStatistics_completionRate() {
        seedTracker("travel-plan", LifeCaseStatus.COMPLETED, 24);
        seedTracker("travel-plan", LifeCaseStatus.FAILED, null);

        given().contentType(ContentType.JSON)
                .when().get("/analytics/cases")
                .then().statusCode(200)
                .body("entries[0].completionRate", equalTo(0.5F));
    }

    // ── SLA Compliance ──

    @Test
    void slaCompliance_computesBreach() {
        Instant now = Instant.now();
        seedWorkItemWithSla("On time", "casehubio/life/household",
                WorkItemStatus.COMPLETED, now.minus(2, ChronoUnit.DAYS),
                now.plus(1, ChronoUnit.DAYS), now);
        seedWorkItemWithSla("Breached", "casehubio/life/household",
                WorkItemStatus.COMPLETED, now.minus(5, ChronoUnit.DAYS),
                now.minus(3, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS));

        given().contentType(ContentType.JSON)
                .when().get("/analytics/sla")
                .then().statusCode(200)
                .body("entries", hasSize(1))
                .body("entries[0].totalWithSla", equalTo(2))
                .body("entries[0].breachedCount", equalTo(1));
    }

    @Test
    void slaCompliance_filterByDomain() {
        Instant now = Instant.now();
        seedWorkItemWithSla("Health", "casehubio/life/health",
                WorkItemStatus.COMPLETED, now.minus(1, ChronoUnit.DAYS),
                now.plus(1, ChronoUnit.DAYS), now);
        seedWorkItemWithSla("Finance", "casehubio/life/finance",
                WorkItemStatus.COMPLETED, now.minus(1, ChronoUnit.DAYS),
                now.plus(1, ChronoUnit.DAYS), now);

        given().contentType(ContentType.JSON)
                .queryParam("domain", "HEALTH")
                .when().get("/analytics/sla")
                .then().statusCode(200)
                .body("entries", hasSize(1))
                .body("entries[0].domain", equalTo("health"));
    }

    @Test
    void slaCompliance_emptyResult() {
        given().contentType(ContentType.JSON)
                .when().get("/analytics/sla")
                .then().statusCode(200)
                .body("entries", hasSize(0));
    }

    @Test
    void slaCompliance_overdueActiveItem_countedAsBreached() {
        Instant now = Instant.now();
        seedWorkItemWithSla("Still active but overdue", "casehubio/life/household",
                WorkItemStatus.PENDING, now.minus(5, ChronoUnit.DAYS),
                now.minus(2, ChronoUnit.DAYS), null);

        given().contentType(ContentType.JSON)
                .when().get("/analytics/sla")
                .then().statusCode(200)
                .body("entries[0].breachedCount", equalTo(1));
    }

    // ── Trust Analytics ──

    @Test
    void trustAnalytics_aggregatesScores() {
        UUID actorId = seedActor("Contractor A");
        seedGlobalTrustScore(LifeActorIds.of(actorId), 0.85);

        given().contentType(ContentType.JSON)
                .when().get("/analytics/trust")
                .then().statusCode(200)
                .body("actorCount", equalTo(1))
                .body("avgGlobalScore", equalTo(0.85F));
    }

    @Test
    void trustAnalytics_emptyWhenNoActors() {
        given().contentType(ContentType.JSON)
                .when().get("/analytics/trust")
                .then().statusCode(200)
                .body("actorCount", equalTo(0))
                .body("avgGlobalScore", nullValue());
    }

    @Test
    void trustAnalytics_excludesErasedActors() {
        UUID actorId = seedActor("Active Contractor");
        seedGlobalTrustScore(LifeActorIds.of(actorId), 0.90);

        UUID erasedId = seedErasedActor("Erased Contractor");
        seedGlobalTrustScore(LifeActorIds.of(erasedId), 0.40);

        given().contentType(ContentType.JSON)
                .when().get("/analytics/trust")
                .then().statusCode(200)
                .body("actorCount", equalTo(1))
                .body("avgGlobalScore", equalTo(0.90F));
    }

    @Test
    void trustAnalytics_lowestScoreActors() {
        UUID a1 = seedActor("High Trust");
        UUID a2 = seedActor("Low Trust");
        seedGlobalTrustScore(LifeActorIds.of(a1), 0.95);
        seedGlobalTrustScore(LifeActorIds.of(a2), 0.30);

        given().contentType(ContentType.JSON)
                .when().get("/analytics/trust")
                .then().statusCode(200)
                .body("lowestScoreActors", hasSize(2))
                .body("lowestScoreActors[0].name", equalTo("Low Trust"));
    }

    @Test
    void trustAnalytics_dimensionAverages() {
        UUID a1 = seedActor("Actor A");
        seedDimensionTrustScore(LifeActorIds.of(a1), "deadline-reliability", 0.80);
        seedDimensionTrustScore(LifeActorIds.of(a1), "cost-accuracy", 0.60);

        given().contentType(ContentType.JSON)
                .when().get("/analytics/trust")
                .then().statusCode(200)
                .body("dimensionAverages.size()", equalTo(2))
                .body("dimensionAverages.'deadline-reliability'", equalTo(0.80F))
                .body("dimensionAverages.'cost-accuracy'", equalTo(0.60F));
    }

    // ── Helpers ──

    @Transactional
    void seedTracker(String caseType, LifeCaseStatus status, Integer resolutionHours) {
        LifeCaseTracker t = new LifeCaseTracker();
        t.caseType     = caseType;
        t.domain       = LifeCaseType.valueOf(caseType.toUpperCase().replace('-', '_')).domain();
        t.status       = status;
        t.engineCaseId = UUID.randomUUID();
        if (resolutionHours != null) {
            t.createdAt = Instant.now().minus(resolutionHours, ChronoUnit.HOURS);
            if (status == LifeCaseStatus.COMPLETED) {
                t.completedAt = Instant.now();
            }
        }
        t.persist();
    }

    @Transactional
    void seedWorkItemWithSla(String title, String scope, WorkItemStatus status,
                             Instant createdAt, Instant expiresAt, Instant completedAt) {
        WorkItem wi = new WorkItem();
        wi.title = title;
        wi.scope = scope;
        wi.status = status;
        wi.tenancyId = "278776f9-e1b0-46fb-9032-8bddebdcf9ce";
        wi.persist();
        wi.createdAt = createdAt;
        wi.expiresAt = expiresAt;
        wi.completedAt = completedAt;
    }

    @Transactional
    UUID seedActor(String name) {
        ExternalActor a = new ExternalActor();
        a.name = name;
        a.actorType = LifeActorType.EXTERNAL_HUMAN;
        a.contactMethod = "phone";
        a.contactValue = "07700900001";
        a.persist();
        return a.id;
    }

    @Transactional
    UUID seedErasedActor(String name) {
        ExternalActor a = new ExternalActor();
        a.name = "[ERASED]";
        a.actorType = LifeActorType.EXTERNAL_HUMAN;
        a.contactMethod = "[ERASED]";
        a.contactValue = "[ERASED]";
        a.gdprErasedAt = Instant.now();
        a.persist();
        return a.id;
    }

    @Transactional
    void seedGlobalTrustScore(String actorId, double score) {
        ActorTrustScore s = new ActorTrustScore();
        s.id = UUID.randomUUID();
        s.actorId = actorId;
        s.scoreType = ScoreType.GLOBAL;
        s.trustScore = score;
        s.globalTrustScore = score;
        s.decisionCount = 5;
        s.lastComputedAt = Instant.now();
        qhorusEm.persist(s);
    }

    @Transactional
    void seedDimensionTrustScore(String actorId, String dimension, double score) {
        ActorTrustScore s = new ActorTrustScore();
        s.id = UUID.randomUUID();
        s.actorId = actorId;
        s.scoreType = ScoreType.GLOBAL;
        s.dimensionKey = dimension;
        s.trustScore = score;
        s.globalTrustScore = score;
        s.decisionCount = 3;
        s.lastComputedAt = Instant.now();
        qhorusEm.persist(s);
    }
}
