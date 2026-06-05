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
class AppointmentCycleIntegrationTest {

    @Inject AppointmentCycleCaseHub caseHub;
    @Inject CaseHubRuntime runtime;
    @Inject CaseInstanceCache cache;
    @Inject WorkItemService workItemService;

    @BeforeEach
    @Transactional
    void seed() {
        LifeTestFixtures.seedStandardTemplates();
    }

    @Test
    void goldenPath_completesAfterHumanTaskCompletion() {
        var caseId = CaseIntegrationTestSupport.startCase(caseHub, runtime, Map.of(
                "appointmentType", "GP",
                "provider", "Dr Smith"
        ));

        CaseIntegrationTestSupport.awaitWorker(runtime, caseId, "pre-visit-prep-agent");

        CaseIntegrationTestSupport.completeHumanTask(workItemService, caseId,
                "{\"notes\": \"Patient seen, follow-up in 2 weeks\"}");

        CaseIntegrationTestSupport.awaitCaseCompleted(cache, caseId);
    }

    @Test
    void declinePath_findsAlternativeAndContinues() {
        var caseId = CaseIntegrationTestSupport.startCase(caseHub, runtime, Map.of(
                "appointmentType", "GP",
                "provider", "unavailable"
        ));

        CaseIntegrationTestSupport.awaitWorker(runtime, caseId, "find-alternative-agent");
        CaseIntegrationTestSupport.awaitWorker(runtime, caseId, "confirm-appointment-agent");

        var workers = CaseIntegrationTestSupport.scheduledWorkerNames(runtime, caseId);
        assertTrue(workers.contains("book-appointment-agent"));
        assertTrue(workers.contains("find-alternative-agent"));
        assertTrue(workers.contains("confirm-appointment-agent"));
    }

    @Test
    void bookingAndConfirmationRunSequentially() {
        var caseId = CaseIntegrationTestSupport.startCase(caseHub, runtime, Map.of(
                "appointmentType", "Specialist",
                "provider", "Dr Jones"
        ));

        CaseIntegrationTestSupport.awaitWorker(runtime, caseId, "confirm-appointment-agent");

        var workers = CaseIntegrationTestSupport.scheduledWorkerNames(runtime, caseId);
        assertTrue(workers.contains("book-appointment-agent"),
                "book-appointment must have fired before confirm-appointment");
    }
}
