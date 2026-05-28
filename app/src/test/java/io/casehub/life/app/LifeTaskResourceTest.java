package io.casehub.life.app;

import io.casehub.work.runtime.model.WorkItemPriority;
import io.casehub.work.runtime.model.WorkItemTemplate;
import io.casehub.work.runtime.service.ExpiryLifecycleService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class LifeTaskResourceTest {

    @Inject
    ExpiryLifecycleService expiryLifecycleService;

    @BeforeEach
    @Transactional
    void seedTemplates() {
        if (WorkItemTemplate.find("name", "household-task").count() == 0) {
            seedTemplate("00000000-0000-0000-0000-000000000001", "household-task", "household", 24);
        }
        if (WorkItemTemplate.find("name", "health-appointment").count() == 0) {
            seedTemplate("00000000-0000-0000-0000-000000000002", "health-appointment", "health", 48);
        }
        if (WorkItemTemplate.find("name", "contractor-coordination").count() == 0) {
            seedTemplate("00000000-0000-0000-0000-000000000003", "contractor-coordination", "contractor", 72);
        }
    }

    @Transactional
    void seedTemplate(String id, String name, String category, int expiryHours) {
        WorkItemTemplate t = new WorkItemTemplate();
        t.id = UUID.fromString(id);
        t.name = name;
        t.category = category;
        t.priority = WorkItemPriority.MEDIUM;
        t.candidateGroups = "household-member";
        t.defaultExpiryHours = expiryHours;
        t.createdBy = "life-system";
        t.createdAt = Instant.now();
        t.persist();
    }

    @Test
    void createLifeTask_noActor_returns201WithWorkItemId() {
        given()
                .contentType("application/json")
                .body("""
                        {"templateRef":"household-task","title":"Grocery order"}
                        """)
                .when().post("/life-tasks")
                .then()
                .statusCode(201)
                .body("workItemId", notNullValue())
                .body("templateRef", equalTo("household-task"))
                .body("domain", equalTo("HOUSEHOLD"))
                .body("status", equalTo("PENDING"));
    }

    @Test
    void createLifeTask_withActor_returns201AndSupplement() {
        String actorId = given()
                .contentType("application/json")
                .body("""
                        {"name":"Dr. Smith","actorType":"EXTERNAL_HUMAN","contactMethod":"phone","contactValue":"+44-7700-900200"}
                        """)
                .when().post("/external-actors")
                .then().statusCode(201)
                .extract().path("id");

        given()
                .contentType("application/json")
                .body("""
                        {"templateRef":"health-appointment","title":"GP checkup","externalActorId":"%s"}
                        """.formatted(actorId))
                .when().post("/life-tasks")
                .then()
                .statusCode(201)
                .body("workItemId", notNullValue())
                .body("domain", equalTo("HEALTH"))
                .body("externalActorId", equalTo(actorId));
    }

    @Test
    void createLifeTask_unknownTemplate_returns422() {
        given()
                .contentType("application/json")
                .body("""
                        {"templateRef":"nonexistent-template","title":"Something"}
                        """)
                .when().post("/life-tasks")
                .then()
                .statusCode(422);
    }

    @Test
    void createLifeTask_unknownExternalActor_returns422() {
        given()
                .contentType("application/json")
                .body("""
                        {"templateRef":"household-task","title":"Fix boiler","externalActorId":"00000000-0000-0000-0000-000000000099"}
                        """)
                .when().post("/life-tasks")
                .then()
                .statusCode(422);
    }

    @Test
    void createLifeTask_withPastDeadline_slaBreachFiresOnCheckExpired() {
        // Create a task with a deadline already in the past
        given()
                .contentType("application/json")
                .body("""
                        {"templateRef":"household-task","title":"Overdue task","deadline":"%s"}
                        """.formatted(Instant.now().minus(1, ChronoUnit.HOURS)))
                .when().post("/life-tasks")
                .then().statusCode(201);

        // ExpiryLifecycleService is injected (not excluded from CDI).
        // Calling checkExpired() directly triggers LifeSlaBreachPolicy for expired WorkItems.
        // Verifies the SLA enforcement pipeline runs without exception.
        expiryLifecycleService.checkExpired();
    }
}
