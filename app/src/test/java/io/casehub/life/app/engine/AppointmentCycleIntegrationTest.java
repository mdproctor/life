/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.life.app.engine;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.api.model.CaseStatus;
import io.casehub.api.model.event.CaseHubEventType;
import io.casehub.engine.common.spi.cache.CaseInstanceCache;
import io.casehub.life.app.LifeTestFixtures;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemStatus;
import io.casehub.work.runtime.service.WorkItemService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Integration tests verifying the appointment-cycle case workflow.
 *
 * <p>Tests start a case via the CaseHub, then use Awaitility to poll the engine
 * until the expected state transitions occur. The humanTask binding (attend-and-record)
 * creates a WorkItem that must be completed programmatically for the case to proceed.
 */
@QuarkusTest
class AppointmentCycleIntegrationTest {

    @Inject AppointmentCycleCaseHub caseHub;
    @Inject CaseHubRuntime caseHubRuntime;
    @Inject CaseInstanceCache caseInstanceCache;
    @Inject WorkItemService workItemService;

    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(100);

    @BeforeEach
    @Transactional
    void seed() {
        LifeTestFixtures.seedStandardTemplates();
    }

    private Set<String> scheduledWorkerNames(UUID caseId) {
        return caseHubRuntime.eventLog(caseId, Set.of(CaseHubEventType.WORKER_SCHEDULED))
                .toCompletableFuture()
                .join()
                .stream()
                .filter(r -> r.metadata() != null && r.metadata().has("workerName"))
                .map(r -> r.metadata().get("workerName").asText())
                .collect(Collectors.toSet());
    }

    private UUID startCase(Map<String, Object> input) {
        UUID caseId = caseHub.startCase(input).toCompletableFuture().join();
        assertNotNull(caseId);

        await().atMost(Duration.ofSeconds(3)).pollInterval(POLL_INTERVAL).until(() ->
                !scheduledWorkerNames(caseId).isEmpty());
        return caseId;
    }

    @Test
    void goldenPath_completesAfterHumanTaskCompletion() {
        UUID caseId = startCase(Map.of(
                "appointmentType", "GP",
                "provider", "Dr Smith"
        ));

        await().atMost(TIMEOUT).pollInterval(POLL_INTERVAL).until(() ->
                scheduledWorkerNames(caseId).contains("pre-visit-prep-agent"));

        String callerRefPrefix = "case:" + caseId;
        var workItemRef = new AtomicReference<WorkItem>();
        await().atMost(TIMEOUT).pollInterval(POLL_INTERVAL).until(() ->
                QuarkusTransaction.requiringNew().call(() -> {
                    WorkItem wi = WorkItem.find("callerRef like ?1 and status = ?2",
                            callerRefPrefix + "%", WorkItemStatus.PENDING).firstResult();
                    if (wi != null) {
                        workItemRef.set(wi);
                        return true;
                    }
                    return false;
                }));
        QuarkusTransaction.requiringNew().run(() ->
                workItemService.completeFromSystem(workItemRef.get().id, "test-actor",
                        "{\"notes\": \"Patient seen, follow-up in 2 weeks\"}"));

        await().atMost(TIMEOUT).pollInterval(POLL_INTERVAL).until(() -> {
            var ci = caseInstanceCache.get(caseId);
            return ci != null && ci.getState() == CaseStatus.COMPLETED;
        });
    }

    @Test
    void declinePath_findsAlternativeAndContinues() {
        UUID caseId = startCase(Map.of(
                "appointmentType", "GP",
                "provider", "unavailable"
        ));

        await().atMost(TIMEOUT).pollInterval(POLL_INTERVAL).until(() -> {
            var workers = scheduledWorkerNames(caseId);
            return workers.contains("book-appointment-agent")
                    && workers.contains("find-alternative-agent")
                    && workers.contains("confirm-appointment-agent");
        });
    }

    @Test
    void bookingAndConfirmationRunSequentially() {
        UUID caseId = startCase(Map.of(
                "appointmentType", "Specialist",
                "provider", "Dr Jones"
        ));

        await().atMost(TIMEOUT).pollInterval(POLL_INTERVAL).until(() ->
                scheduledWorkerNames(caseId).contains("confirm-appointment-agent"));

        var workers = scheduledWorkerNames(caseId);
        assertTrue(workers.contains("book-appointment-agent"),
                "book-appointment must have fired before confirm-appointment");
    }
}
