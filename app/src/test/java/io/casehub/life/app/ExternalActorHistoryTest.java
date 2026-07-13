package io.casehub.life.app;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.life.api.LifeActorType;
import io.casehub.life.api.LifeDomain;
import io.casehub.life.app.entity.ExternalActor;
import io.casehub.life.app.entity.LifeTaskContext;
import io.casehub.platform.api.identity.ActorType;
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
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestSecurity(user = "household-admin", roles = {"household-admin"})
class ExternalActorHistoryTest {

    private static final UUID ACTOR_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    @Inject
    @io.quarkus.hibernate.orm.PersistenceUnit("qhorus")
    EntityManager qhorusEm;

    @BeforeEach
    @Transactional
    void seed() {
        ExternalActor.deleteAll();
        LifeTaskContext.deleteAll();
        WorkItem.deleteAll();

        ExternalActor actor = new ExternalActor();
        actor.id = ACTOR_ID;
        actor.name = "Test Contractor";
        actor.actorType = LifeActorType.EXTERNAL_HUMAN;
        actor.contactMethod = "phone";
        actor.contactValue = "07700900001";
        actor.persist();

        LifeTestFixtures.seedStandardTemplates();
        qhorusEm.createQuery("DELETE FROM LedgerAttestation").executeUpdate();
    }

    @Test
    void trustHistory_returnsAttestations() {
        seedAttestation(ACTOR_ID, "household-management", "deadline-reliability", 0.85, AttestationVerdict.SOUND);
        seedAttestation(ACTOR_ID, "contractor-coordination", null, null, AttestationVerdict.FLAGGED);

        given().contentType(ContentType.JSON)
                .when().get("/external-actors/" + ACTOR_ID + "/trust-history")
                .then().statusCode(200)
                .body("items", hasSize(2))
                .body("items[0].capabilityTag", notNullValue())
                .body("items[0].verdict", notNullValue());
    }

    @Test
    void trustHistory_verdictOnlyAttestation_hasNullScoreAndDimension() {
        seedAttestation(ACTOR_ID, "contractor-coordination", null, null, AttestationVerdict.FLAGGED);

        given().contentType(ContentType.JSON)
                .when().get("/external-actors/" + ACTOR_ID + "/trust-history")
                .then().statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].dimension", nullValue())
                .body("items[0].score", nullValue())
                .body("items[0].verdict", equalTo("FLAGGED"));
    }

    @Test
    void trustHistory_dimensionAttestation_hasScoreAndDimension() {
        seedAttestation(ACTOR_ID, "household-management", "deadline-reliability", 0.92, AttestationVerdict.SOUND);

        given().contentType(ContentType.JSON)
                .when().get("/external-actors/" + ACTOR_ID + "/trust-history")
                .then().statusCode(200)
                .body("items[0].dimension", equalTo("deadline-reliability"))
                .body("items[0].score", equalTo(0.92F))
                .body("items[0].verdict", equalTo("SOUND"));
    }

    @Test
    void trustHistory_unknownActor_returns404() {
        given().contentType(ContentType.JSON)
                .when().get("/external-actors/" + UUID.randomUUID() + "/trust-history")
                .then().statusCode(404);
    }

    @Test
    void trustHistory_paginated() {
        for (int i = 0; i < 5; i++) {
            seedAttestation(ACTOR_ID, "household-management", "deadline-reliability",
                    0.8 + i * 0.02, AttestationVerdict.SOUND);
        }

        given().contentType(ContentType.JSON)
                .queryParam("page", 0).queryParam("size", 2)
                .when().get("/external-actors/" + ACTOR_ID + "/trust-history")
                .then().statusCode(200)
                .body("items", hasSize(2))
                .body("totalCount", equalTo(5));
    }

    @Test
    void trustHistory_noAttestations_returnsEmptyList() {
        given().contentType(ContentType.JSON)
                .when().get("/external-actors/" + ACTOR_ID + "/trust-history")
                .then().statusCode(200)
                .body("items", hasSize(0))
                .body("totalCount", equalTo(0));
    }

    @Test
    void activity_returnsWorkItemsForActor() {
        UUID wiId = seedWorkItemWithContext(ACTOR_ID, "Fix boiler", LifeDomain.HOUSEHOLD, WorkItemStatus.COMPLETED);

        given().contentType(ContentType.JSON)
                .when().get("/external-actors/" + ACTOR_ID + "/activity")
                .then().statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].workItemId", equalTo(wiId.toString()))
                .body("items[0].title", equalTo("Fix boiler"))
                .body("items[0].domain", equalTo("HOUSEHOLD"));
    }

    @Test
    void activity_unknownActor_returns404() {
        given().contentType(ContentType.JSON)
                .when().get("/external-actors/" + UUID.randomUUID() + "/activity")
                .then().statusCode(404);
    }

    @Test
    void activity_noWorkItems_returnsEmptyList() {
        given().contentType(ContentType.JSON)
                .when().get("/external-actors/" + ACTOR_ID + "/activity")
                .then().statusCode(200)
                .body("items", hasSize(0))
                .body("totalCount", equalTo(0));
    }

    @Test
    void activity_orphanedContext_excludedFromCountAndItems() {
        seedWorkItemWithContext(ACTOR_ID, "Valid task", LifeDomain.HOUSEHOLD, WorkItemStatus.PENDING);
        seedOrphanedContext(ACTOR_ID, LifeDomain.FINANCE);

        given().contentType(ContentType.JSON)
                .when().get("/external-actors/" + ACTOR_ID + "/activity")
                .then().statusCode(200)
                .body("items", hasSize(1))
                .body("totalCount", equalTo(1))
                .body("items[0].title", equalTo("Valid task"));
    }

    @Test
    void activity_paginated() {
        for (int i = 0; i < 5; i++) {
            seedWorkItemWithContext(ACTOR_ID, "Task " + i, LifeDomain.HOUSEHOLD, WorkItemStatus.PENDING);
        }

        given().contentType(ContentType.JSON)
                .queryParam("page", 0).queryParam("size", 2)
                .when().get("/external-actors/" + ACTOR_ID + "/activity")
                .then().statusCode(200)
                .body("items", hasSize(2))
                .body("totalCount", equalTo(5));
    }

    @Transactional
    void seedAttestation(UUID subjectId, String capabilityTag, String dimension,
                         Double score, AttestationVerdict verdict) {
        LedgerAttestation a = new LedgerAttestation();
        a.subjectId = subjectId;
        a.attestorId = "life-system";
        a.attestorType = ActorType.AGENT;
        a.capabilityTag = capabilityTag;
        a.trustDimension = dimension;
        a.dimensionScore = score;
        a.verdict = verdict;
        a.confidence = 1.0;
        a.ledgerEntryId = UUID.randomUUID();
        qhorusEm.persist(a);
    }

    @Transactional
    UUID seedWorkItemWithContext(UUID actorId, String title, LifeDomain domain, WorkItemStatus status) {
        WorkItem wi = new WorkItem();
        wi.title = title;
        wi.status = status;
        wi.scope = "casehubio/life/" + domain.descriptor().templateCategory();
        wi.tenancyId = "278776f9-e1b0-46fb-9032-8bddebdcf9ce";
        if (status == WorkItemStatus.COMPLETED) wi.completedAt = Instant.now();
        wi.persist();

        LifeTaskContext ctx = new LifeTaskContext();
        ctx.workItemId = wi.id;
        ctx.domain = domain;
        ctx.externalActorId = actorId;
        ctx.persist();

        return wi.id;
    }

    @Transactional
    void seedOrphanedContext(UUID actorId, LifeDomain domain) {
        LifeTaskContext ctx = new LifeTaskContext();
        ctx.workItemId = UUID.randomUUID();
        ctx.domain = domain;
        ctx.externalActorId = actorId;
        ctx.persist();
    }
}
