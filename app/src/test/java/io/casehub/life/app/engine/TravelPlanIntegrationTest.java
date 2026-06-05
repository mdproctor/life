package io.casehub.life.app.engine;

import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.engine.common.spi.cache.CaseInstanceCache;
import io.casehub.life.app.LifeTestFixtures;
import io.casehub.work.runtime.service.WorkItemService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class TravelPlanIntegrationTest {

    @Inject TravelPlanCaseHub caseHub;
    @Inject CaseHubRuntime runtime;
    @Inject CaseInstanceCache cache;
    @Inject WorkItemService workItemService;

    @BeforeEach
    @Transactional
    void seed() {
        LifeTestFixtures.seedStandardTemplates();
    }

    @Test
    void approvalPath_completesAfterHumanTask() {
        var caseId = CaseIntegrationTestSupport.startCase(caseHub, runtime, Map.of(
                "request", Map.of("destination", "Barcelona", "dates", "July 2026"),
                "selectedDestination", Map.of("name", "Barcelona", "estimatedCost", 1800)
        ));

        CaseIntegrationTestSupport.awaitWorker(runtime, caseId, "budget-assessment-agent");

        CaseIntegrationTestSupport.completeHumanTask(workItemService, caseId,
                "{\"approved\": true, \"approver\": \"household-admin\"}");

        CaseIntegrationTestSupport.awaitWorker(runtime, caseId, "confirmation-agent");
        CaseIntegrationTestSupport.awaitCaseCompleted(cache, caseId);
    }

    @Test
    void parallelSearchesBothFire() {
        var caseId = CaseIntegrationTestSupport.startCase(caseHub, runtime, Map.of(
                "request", Map.of("destination", "Tokyo", "dates", "August 2026"),
                "selectedDestination", Map.of("name", "Tokyo", "estimatedCost", 4500)
        ));

        // Await one, then assert the other is already present — proves parallelism
        CaseIntegrationTestSupport.awaitWorker(runtime, caseId, "flight-search-agent");
        assertTrue(CaseIntegrationTestSupport.scheduledWorkerNames(runtime, caseId)
                .contains("hotel-search-agent"), "hotel-search should fire in parallel with flight-search");
    }
}
