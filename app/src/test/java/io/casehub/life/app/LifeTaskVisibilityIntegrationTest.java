package io.casehub.life.app;

import io.casehub.life.api.HouseholdGroups;
import io.casehub.life.api.LifeDomain;
import io.casehub.life.app.entity.LifeTaskContext;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemType;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Integration test for {@link io.casehub.life.api.spi.LifeTaskVisibilityPolicy}
 * wiring in the REST resource layer.
 *
 * <p>Seeds two WorkItems directly via Panache: one assigned to admin (not visible
 * to junior), one assigned to junior (visible to junior). Validates the full
 * resource → visibility-policy → CurrentPrincipal pipeline.
 */
@QuarkusTest
@TestSecurity(user = "admin-user", roles = "household-admin")
class LifeTaskVisibilityIntegrationTest {

    private static final String ADMIN_ACTOR = "admin-actor";
    private static final String JUNIOR_ACTOR = "junior-actor";

    @Inject
    FixedCurrentPrincipal fixedPrincipal;

    private UUID adminTaskId;
    private UUID juniorTaskId;

    @BeforeEach
    @Transactional
    void setUp() {
        LifeTestFixtures.seedStandardTemplates();

        // Task assigned to admin with admin-only candidate groups.
        adminTaskId = seedWorkItem(ADMIN_ACTOR, HouseholdGroups.ADMIN);

        // Task assigned to junior with junior candidate groups.
        juniorTaskId = seedWorkItem(JUNIOR_ACTOR, HouseholdGroups.JUNIOR);
    }

    @AfterEach
    void resetPrincipal() {
        fixedPrincipal.reset();
    }

    @Test
    void adminCanSeeAdminTask() {
        fixedPrincipal.setActorId(ADMIN_ACTOR);
        fixedPrincipal.setGroups(Set.of(HouseholdGroups.ADMIN));

        given()
            .when().get("/life-tasks/" + adminTaskId)
            .then()
            .statusCode(200)
            .body("workItemId", equalTo(adminTaskId.toString()));
    }

    @Test
    @TestSecurity(user = "junior-user", roles = "household-junior")
    void juniorCannotSeeAdminTask() {
        fixedPrincipal.setActorId(JUNIOR_ACTOR);
        fixedPrincipal.setGroups(Set.of(HouseholdGroups.JUNIOR));

        given()
            .when().get("/life-tasks/" + adminTaskId)
            .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "junior-user", roles = "household-junior")
    void juniorCanSeeOwnAssignedTask() {
        fixedPrincipal.setActorId(JUNIOR_ACTOR);
        fixedPrincipal.setGroups(Set.of(HouseholdGroups.JUNIOR));

        given()
            .when().get("/life-tasks/" + juniorTaskId)
            .then()
            .statusCode(200)
            .body("workItemId", equalTo(juniorTaskId.toString()));
    }

    private UUID seedWorkItem(String assigneeId, String candidateGroups) {
        WorkItem wi = new WorkItem();
        wi.title = "Task for " + assigneeId;
        wi.types = java.util.Set.of(new WorkItemType("household"));
        wi.status = WorkItemStatus.PENDING;
        wi.candidateGroups = candidateGroups;
        wi.assigneeId = assigneeId;
        wi.createdBy = "life-system";
        wi.callerRef = "life:task/household-task";
        wi.scope = "casehubio/life/household";
        wi.tenancyId = "278776f9-e1b0-46fb-9032-8bddebdcf9ce";
        wi.persist();

        LifeTaskContext ctx = new LifeTaskContext();
        ctx.workItemId = wi.id;
        ctx.domain = LifeDomain.HOUSEHOLD;
        ctx.persist();

        return wi.id;
    }
}
