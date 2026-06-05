package io.casehub.life.app.engine;

import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.life.app.LifeTestFixtures;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

@QuarkusTest
class FinancialReviewIntegrationTest {

    @Inject FinancialReviewCaseHub caseHub;
    @Inject CaseHubRuntime runtime;

    @BeforeEach
    @Transactional
    void seed() {
        LifeTestFixtures.seedStandardTemplates();
    }

    @Test
    void workersFireThroughEscalation() {
        var caseId = CaseIntegrationTestSupport.startCase(caseHub, runtime, Map.of(
                "reviewPeriod", Map.of("month", "May", "year", 2026)
        ));

        CaseIntegrationTestSupport.awaitWorker(runtime, caseId, "analyse-anomalies-agent");
        CaseIntegrationTestSupport.awaitWorker(runtime, caseId, "escalate-anomalies-agent");
    }
}
