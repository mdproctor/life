package io.casehub.life.app.engine;

import io.casehub.engine.common.spi.cache.CaseInstanceCache;
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
class FamilyVoteIntegrationTest {

    @Inject FamilyVoteCaseHub caseHub;
    @Inject CaseInstanceCache cache;
    @Inject WorkItemService workItemService;

    @BeforeEach
    @Transactional
    void seed() {
        LifeTestFixtures.seedStandardTemplates();
    }

    @Test
    void goldenPath_humanTaskToCompletion() {
        // family-vote is a pure humanTask case — no workers fire, so startCase() is not used
        var caseId = caseHub.startCase(Map.of(
                "proposal", "Family holiday to Barcelona",
                "estimatedCost", 3500
        )).toCompletableFuture().join();
        assertNotNull(caseId);

        CaseIntegrationTestSupport.completeHumanTask(workItemService, caseId,
                "{\"approved\": true, \"voter\": \"member-1\"}");

        CaseIntegrationTestSupport.awaitCaseCompleted(cache, caseId);
    }
}
