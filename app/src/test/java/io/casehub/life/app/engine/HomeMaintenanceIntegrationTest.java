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
class HomeMaintenanceIntegrationTest {

    @Inject HomeMaintenanceCaseHub caseHub;
    @Inject CaseHubRuntime runtime;
    @Inject WorkItemService workItemService;

    @BeforeEach
    @Transactional
    void seed() {
        LifeTestFixtures.seedStandardTemplates();
    }

    @Test
    void workersFireAndHumanTaskCreated() {
        var caseId = CaseIntegrationTestSupport.startCase(caseHub, runtime, Map.of(
                "request", Map.of("issue", "Roof leak", "urgency", "medium")
        ));

        CaseIntegrationTestSupport.awaitWorker(runtime, caseId, "get-quotes-agent");

        var wi = CaseIntegrationTestSupport.findPendingHumanTask(caseId);
        assertNotNull(wi, "approve-contractor humanTask should be created");
    }

    @Test
    void afterApproval_issueCommitmentFires() {
        var caseId = CaseIntegrationTestSupport.startCase(caseHub, runtime, Map.of(
                "request", Map.of("issue", "Boiler service", "urgency", "low")
        ));

        CaseIntegrationTestSupport.awaitWorker(runtime, caseId, "get-quotes-agent");
        CaseIntegrationTestSupport.completeHumanTask(workItemService, caseId,
                "{\"selectedContractor\": \"ABC Roofing\", \"approvedAmount\": 4500}");

        CaseIntegrationTestSupport.awaitWorker(runtime, caseId, "issue-commitment-agent");
    }
}
