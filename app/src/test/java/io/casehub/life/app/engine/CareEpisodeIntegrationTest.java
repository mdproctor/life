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

@QuarkusTest
class CareEpisodeIntegrationTest {

    @Inject CareEpisodeCaseHub caseHub;
    @Inject CaseHubRuntime runtime;
    @Inject CaseInstanceCache cache;
    @Inject WorkItemService workItemService;

    @BeforeEach
    @Transactional
    void seed() {
        LifeTestFixtures.seedStandardTemplates();
    }

    @Test
    void goldenPath_workersAndHumanTaskToCompletion() {
        var caseId = CaseIntegrationTestSupport.startCase(caseHub, runtime, Map.of(
                "careRequest", Map.of("patient", "Grandma", "concern", "routine check")
        ));

        CaseIntegrationTestSupport.awaitWorker(runtime, caseId, "provide-care-agent");

        CaseIntegrationTestSupport.completeHumanTask(workItemService, caseId,
                "{\"notes\": \"Patient stable, medication administered\"}");

        CaseIntegrationTestSupport.awaitCaseCompleted(cache, caseId);
    }
}
