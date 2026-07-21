/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.life.app.resource;

import io.casehub.life.api.HouseholdGroups;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.casehub.life.api.LifeCaseStatus;
import io.casehub.life.api.LifeDomain;
import io.casehub.life.app.LifeTestFixtures;
import io.casehub.life.app.entity.LifeCaseTracker;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
@TestSecurity(user = "household-admin", roles = {HouseholdGroups.ADMIN})
class LifeCaseResourceTest {

    private static final UUID TRAVEL_ID    = UUID.fromString("d0000000-0000-0000-0000-000000000001");
    private static final UUID HOUSEHOLD_ID = UUID.fromString("d0000000-0000-0000-0000-000000000002");
    private static final UUID FINANCE_ID   = UUID.fromString("d0000000-0000-0000-0000-000000000003");

    @Inject
    FixedCurrentPrincipal currentPrincipal;

    @BeforeEach
    @Transactional
    void setup() {
        currentPrincipal.setGroups(Set.of(HouseholdGroups.ADMIN));
        LifeTestFixtures.seedStandardTemplates();
        LifeTestFixtures.seedEscalationTemplate();
        seedTracker(TRAVEL_ID, "travel-plan", LifeDomain.TRAVEL, LifeCaseStatus.ACTIVE);
        seedTracker(HOUSEHOLD_ID, "home-maintenance", LifeDomain.HOUSEHOLD, LifeCaseStatus.COMPLETED);
        seedTracker(FINANCE_ID, "financial-review", LifeDomain.FINANCE, LifeCaseStatus.ACTIVE);
    }

    @AfterEach
    @Transactional
    void cleanup() {
        currentPrincipal.reset();
        LifeCaseTracker.deleteAll();
    }

    @Test
    void createCase_returnsCreated() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"caseType\": \"APPOINTMENT_CYCLE\", \"context\": {\"appointmentType\": \"GP\"}}")
                .when()
                .post("/life-cases")
                .then()
                .statusCode(201)
                .body("caseType", equalTo("APPOINTMENT_CYCLE"))
                .body("status", equalTo("ACTIVE"))
                .body("caseId", notNullValue());
    }

    @Test
    void listReturnsSeededCases() {
        given()
                .when().get("/life-cases")
                .then()
                .statusCode(200)
                .body("items.size()", greaterThanOrEqualTo(3))
                .body("totalCount", greaterThanOrEqualTo(3));
    }

    @Test
    void listFiltersByDomain() {
        given()
                .queryParam("domain", "TRAVEL")
                .when().get("/life-cases")
                .then()
                .statusCode(200)
                .body("items.size()", is(1))
                .body("items[0].domain", is("TRAVEL"));
    }

    @Test
    void listFiltersByStatus() {
        given()
                .queryParam("status", "COMPLETED")
                .when().get("/life-cases")
                .then()
                .statusCode(200)
                .body("items.size()", is(1))
                .body("items[0].status", is("COMPLETED"));
    }

    @Test
    void getByIdReturnsDetail() {
        given()
                .when().get("/life-cases/" + FINANCE_ID)
                .then()
                .statusCode(200)
                .body("caseId", is(FINANCE_ID.toString()))
                .body("domain", is("FINANCE"));
    }

    @Test
    void getByIdReturns404ForUnknown() {
        given()
                .when().get("/life-cases/" + UUID.randomUUID())
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "junior", roles = {HouseholdGroups.JUNIOR})
    void juniorSeesEmptyList() {
        currentPrincipal.setGroups(Set.of(HouseholdGroups.JUNIOR));
        given()
                .when().get("/life-cases")
                .then()
                .statusCode(200)
                .body("items.size()", is(0))
                .body("totalCount", is(0));
    }

    private static void seedTracker(UUID id, String caseType, LifeDomain domain, LifeCaseStatus status) {
        if (LifeCaseTracker.findById(id) != null) {return;}
        LifeCaseTracker t = new LifeCaseTracker();
        t.id        = id;
        t.caseType  = caseType;
        t.domain    = domain;
        t.status    = status;
        t.createdAt = Instant.now();
        if (status == LifeCaseStatus.COMPLETED) {t.completedAt = Instant.now();}
        t.persist();
    }
}
