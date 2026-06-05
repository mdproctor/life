package io.casehub.life.app.engine;

import io.casehub.api.engine.CaseHub;
import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.api.model.CaseStatus;
import io.casehub.api.model.event.CaseHubEventType;
import io.casehub.engine.common.spi.cache.CaseInstanceCache;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemStatus;
import io.casehub.work.runtime.service.WorkItemService;
import io.quarkus.narayana.jta.QuarkusTransaction;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class CaseIntegrationTestSupport {

    static final Duration TIMEOUT = Duration.ofSeconds(15);
    static final Duration POLL_INTERVAL = Duration.ofMillis(100);

    private CaseIntegrationTestSupport() {}

    static Set<String> scheduledWorkerNames(CaseHubRuntime runtime, UUID caseId) {
        return runtime.eventLog(caseId, Set.of(CaseHubEventType.WORKER_SCHEDULED))
                .toCompletableFuture()
                .join()
                .stream()
                .filter(r -> r.metadata() != null && r.metadata().has("workerName"))
                .map(r -> r.metadata().get("workerName").asText())
                .collect(Collectors.toSet());
    }

    static UUID startCase(CaseHub caseHub, CaseHubRuntime runtime, Map<String, Object> input) {
        UUID caseId = caseHub.startCase(input).toCompletableFuture().join();
        assertNotNull(caseId);
        await().atMost(Duration.ofSeconds(5)).pollInterval(POLL_INTERVAL).until(() ->
                !scheduledWorkerNames(runtime, caseId).isEmpty());
        return caseId;
    }

    static void awaitWorker(CaseHubRuntime runtime, UUID caseId, String workerName) {
        await().atMost(TIMEOUT).pollInterval(POLL_INTERVAL).until(() ->
                scheduledWorkerNames(runtime, caseId).contains(workerName));
    }

    static WorkItem findPendingHumanTask(UUID caseId) {
        String callerRefPrefix = "case:" + caseId;
        var ref = new AtomicReference<WorkItem>();
        await().atMost(TIMEOUT).pollInterval(POLL_INTERVAL).until(() ->
                QuarkusTransaction.requiringNew().call(() -> {
                    WorkItem wi = WorkItem.find("callerRef like ?1 and status = ?2",
                            callerRefPrefix + "%", WorkItemStatus.PENDING).firstResult();
                    if (wi != null) {
                        ref.set(wi);
                        return true;
                    }
                    return false;
                }));
        return ref.get();
    }

    static void completeHumanTask(WorkItemService workItemService, UUID caseId, String output) {
        WorkItem wi = findPendingHumanTask(caseId);
        QuarkusTransaction.requiringNew().run(() ->
                workItemService.completeFromSystem(wi.id, "test-actor", output));
    }

    static void awaitCaseCompleted(CaseInstanceCache cache, UUID caseId) {
        await().atMost(TIMEOUT).pollInterval(POLL_INTERVAL).until(() -> {
            var ci = cache.get(caseId);
            return ci != null && ci.getState() == CaseStatus.COMPLETED;
        });
    }
}
