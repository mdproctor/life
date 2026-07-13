package io.casehub.life.app;

import io.casehub.life.api.LifeDomain;
import io.casehub.life.app.entity.LifeTaskContext;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.runtime.model.WorkItem;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestSecurity(user = "household-admin", roles = {"household-admin"})
class PendingActionsTest {

    @BeforeEach
    @Transactional
    void seed() {
        LifeTaskContext.deleteAll();
        WorkItem.deleteAll();
        LifeTestFixtures.seedStandardTemplates();
    }

    @Test
    void pendingActions_returnsActiveLifeWorkItems() {
        seedWorkItem("Approve quote", WorkItemStatus.PENDING, "casehubio/life/household",
                "household-admin", Instant.now().plus(2, ChronoUnit.DAYS), LifeDomain.HOUSEHOLD);

        given().contentType(ContentType.JSON)
                .when().get("/pending-actions")
                .then().statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].title", equalTo("Approve quote"))
                .body("items[0].urgency", equalTo("NORMAL"));
    }

    @Test
    void pendingActions_excludesSuspended() {
        seedWorkItem("Suspended task", WorkItemStatus.SUSPENDED, "casehubio/life/household",
                "household-admin", Instant.now().plus(1, ChronoUnit.DAYS), LifeDomain.HOUSEHOLD);

        given().contentType(ContentType.JSON)
                .when().get("/pending-actions")
                .then().statusCode(200)
                .body("items", hasSize(0));
    }

    @Test
    void pendingActions_excludesCompleted() {
        seedWorkItem("Done task", WorkItemStatus.COMPLETED, "casehubio/life/household",
                "household-admin", null, LifeDomain.HOUSEHOLD);

        given().contentType(ContentType.JSON)
                .when().get("/pending-actions")
                .then().statusCode(200)
                .body("items", hasSize(0));
    }

    @Test
    void pendingActions_excludesNonLifeScope() {
        seedWorkItem("Other scope", WorkItemStatus.PENDING, "casehubio/other/thing",
                "admin", null, null);

        given().contentType(ContentType.JSON)
                .when().get("/pending-actions")
                .then().statusCode(200)
                .body("items", hasSize(0));
    }

    @Test
    void pendingActions_overdueClassification() {
        seedWorkItem("Overdue task", WorkItemStatus.PENDING, "casehubio/life/health",
                "household-member", Instant.now().minus(3, ChronoUnit.DAYS), LifeDomain.HEALTH);

        given().contentType(ContentType.JSON)
                .when().get("/pending-actions")
                .then().statusCode(200)
                .body("items[0].urgency", equalTo("OVERDUE"))
                .body("items[0].daysOverdue", greaterThanOrEqualTo(2));
    }

    @Test
    void pendingActions_dueSoonClassification() {
        seedWorkItem("Soon task", WorkItemStatus.PENDING, "casehubio/life/finance",
                "household-admin", Instant.now().plus(6, ChronoUnit.HOURS), LifeDomain.FINANCE);

        given().contentType(ContentType.JSON)
                .when().get("/pending-actions")
                .then().statusCode(200)
                .body("items[0].urgency", equalTo("DUE_SOON"));
    }

    @Test
    void pendingActions_filterByDomain() {
        seedWorkItem("Health task", WorkItemStatus.PENDING, "casehubio/life/health",
                "household-member", null, LifeDomain.HEALTH);
        seedWorkItem("Finance task", WorkItemStatus.PENDING, "casehubio/life/finance",
                "household-admin", null, LifeDomain.FINANCE);

        given().contentType(ContentType.JSON)
                .queryParam("domain", "HEALTH")
                .when().get("/pending-actions")
                .then().statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].title", equalTo("Health task"));
    }

    @Test
    void pendingActions_filterByCandidateGroup() {
        seedWorkItem("Admin task", WorkItemStatus.PENDING, "casehubio/life/household",
                "household-admin", null, LifeDomain.HOUSEHOLD);
        seedWorkItem("Member task", WorkItemStatus.PENDING, "casehubio/life/household",
                "household-member", null, LifeDomain.HOUSEHOLD);

        given().contentType(ContentType.JSON)
                .queryParam("candidateGroup", "household-admin")
                .when().get("/pending-actions")
                .then().statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].title", equalTo("Admin task"));
    }

    @Test
    void pendingActions_customDueSoonHours() {
        seedWorkItem("Task", WorkItemStatus.PENDING, "casehubio/life/household",
                "household-admin", Instant.now().plus(6, ChronoUnit.HOURS), LifeDomain.HOUSEHOLD);

        given().contentType(ContentType.JSON)
                .queryParam("dueSoonHours", 4)
                .when().get("/pending-actions")
                .then().statusCode(200)
                .body("items[0].urgency", equalTo("NORMAL"));

        given().contentType(ContentType.JSON)
                .queryParam("dueSoonHours", 8)
                .when().get("/pending-actions")
                .then().statusCode(200)
                .body("items[0].urgency", equalTo("DUE_SOON"));
    }

    @Test
    void pendingActions_sortOrder_overdueFirst() {
        seedWorkItem("Normal", WorkItemStatus.PENDING, "casehubio/life/household",
                "household-admin", Instant.now().plus(3, ChronoUnit.DAYS), LifeDomain.HOUSEHOLD);
        seedWorkItem("Overdue", WorkItemStatus.PENDING, "casehubio/life/household",
                "household-admin", Instant.now().minus(1, ChronoUnit.DAYS), LifeDomain.HOUSEHOLD);

        given().contentType(ContentType.JSON)
                .when().get("/pending-actions")
                .then().statusCode(200)
                .body("items[0].title", equalTo("Overdue"))
                .body("items[1].title", equalTo("Normal"));
    }

    @Test
    void pendingActions_sortOrder_crossPage_overdueOnFirstPage() {
        seedWorkItem("Normal1", WorkItemStatus.PENDING, "casehubio/life/household",
                "household-admin", Instant.now().plus(5, ChronoUnit.DAYS), LifeDomain.HOUSEHOLD);
        seedWorkItem("Normal2", WorkItemStatus.PENDING, "casehubio/life/household",
                "household-admin", Instant.now().plus(4, ChronoUnit.DAYS), LifeDomain.HOUSEHOLD);
        seedWorkItem("Overdue", WorkItemStatus.PENDING, "casehubio/life/household",
                "household-admin", Instant.now().minus(1, ChronoUnit.DAYS), LifeDomain.HOUSEHOLD);

        given().contentType(ContentType.JSON)
                .queryParam("page", 0).queryParam("size", 2)
                .when().get("/pending-actions")
                .then().statusCode(200)
                .body("items", hasSize(2))
                .body("items[0].title", equalTo("Overdue"));
    }

    @Test
    void pendingActions_negativePage_clampsToZero() {
        seedWorkItem("Task", WorkItemStatus.PENDING, "casehubio/life/household",
                "household-admin", null, LifeDomain.HOUSEHOLD);

        given().contentType(ContentType.JSON)
                .queryParam("page", -1)
                .when().get("/pending-actions")
                .then().statusCode(200)
                .body("items", hasSize(1));
    }

    @Test
    void pendingActions_zeroSize_clampsToOne() {
        seedWorkItem("Task", WorkItemStatus.PENDING, "casehubio/life/household",
                "household-admin", null, LifeDomain.HOUSEHOLD);

        given().contentType(ContentType.JSON)
                .queryParam("size", 0)
                .when().get("/pending-actions")
                .then().statusCode(200)
                .body("items", hasSize(1));
    }

    @Test
    void pendingActions_noDeadline() {
        seedWorkItem("No deadline", WorkItemStatus.PENDING, "casehubio/life/household",
                "household-admin", null, LifeDomain.HOUSEHOLD);

        given().contentType(ContentType.JSON)
                .when().get("/pending-actions")
                .then().statusCode(200)
                .body("items[0].urgency", equalTo("NO_DEADLINE"))
                .body("items[0].daysOverdue", nullValue());
    }

    @Test
    void pendingActions_domainResolvedFromScope_whenNoLifeTaskContext() {
        seedWorkItem("Scopeonly task", WorkItemStatus.PENDING, "casehubio/life/household",
                "household-admin", null, null);

        given().contentType(ContentType.JSON)
                .when().get("/pending-actions")
                .then().statusCode(200)
                .body("items[0].domain", equalTo("HOUSEHOLD"));
    }

    @Transactional
    void seedWorkItem(String title, WorkItemStatus status, String scope,
                      String candidateGroups, Instant expiresAt, LifeDomain domain) {
        WorkItem wi = new WorkItem();
        wi.title = title;
        wi.status = status;
        wi.scope = scope;
        wi.candidateGroups = candidateGroups;
        wi.expiresAt = expiresAt;
        wi.tenancyId = "278776f9-e1b0-46fb-9032-8bddebdcf9ce";
        wi.persist();

        if (domain != null) {
            LifeTaskContext ctx = new LifeTaskContext();
            ctx.workItemId = wi.id;
            ctx.domain = domain;
            ctx.persist();
        }
    }
}
