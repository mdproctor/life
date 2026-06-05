package io.casehub.life.app.engine;

import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.life.app.LifeTestFixtures;
import io.casehub.work.runtime.service.WorkItemService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class CareCoordinationIntegrationTest {

    @Inject CareCoordinationCaseHub caseHub;
    @Inject CaseHubRuntime runtime;
    @Inject WorkItemService workItemService;

    @BeforeEach
    @Transactional
    void seed() {
        LifeTestFixtures.seedStandardTemplates();
    }

    @Test
    void workersFireAndAssignCarerCreated() {
        var caseId = CaseIntegrationTestSupport.startCase(caseHub, runtime, Map.of(
                "careRequest", Map.of("patient", "Grandma", "need", "daily assistance")
        ));

        CaseIntegrationTestSupport.awaitWorker(runtime, caseId, "care-plan-agent");

        var wi = CaseIntegrationTestSupport.findPendingHumanTask(caseId);
        assertNotNull(wi, "assign-carer humanTask should be created");
    }
}
