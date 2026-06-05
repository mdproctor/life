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
class ContractorCoordinationIntegrationTest {

    @Inject ContractorCoordinationCaseHub caseHub;
    @Inject CaseHubRuntime runtime;

    @BeforeEach
    @Transactional
    void seed() {
        LifeTestFixtures.seedStandardTemplates();
    }

    @Test
    void requestQuoteFires() {
        var caseId = CaseIntegrationTestSupport.startCase(caseHub, runtime, Map.of(
                "contractorRequest", Map.of("service", "Plumbing repair", "deadline", "2026-07-01")
        ));

        CaseIntegrationTestSupport.awaitWorker(runtime, caseId, "request-quote-agent");
    }
}
