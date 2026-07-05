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

import io.casehub.api.engine.CaseHub;
import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.life.api.LifeCaseStatus;
import io.casehub.life.api.LifeCaseType;
import io.casehub.life.api.request.CreateLifeCaseRequest;
import io.casehub.life.api.response.LifeCaseResponse;
import io.casehub.life.app.entity.LifeCaseTracker;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for starting engine cases using the three-phase pattern to avoid Agroal connection pool
 * deadlock. Per PP-20260529-3ffe28:
 *
 * <p>Phase 1 (@Transactional): validate, create LifeCaseTracker(ACTIVE), build initial context
 * Phase 2 (no transaction): caseHub.startCase(initialContext).join() Phase 3 (@Transactional):
 * persist engineCaseId, signal caseId into context Error recovery (@Transactional, in catch):
 * markFailed()
 *
 * <p>Each phase is a separate method with method-level transaction boundaries via CDI proxy.
 */
@ApplicationScoped
public class LifeCaseService {

    private static final Logger LOG = Logger.getLogger(LifeCaseService.class);

    @Inject @Any Instance<LifeTypedCaseHub> caseHubs;
    @Inject CaseHubRuntime caseHubRuntime;

    public LifeCaseResponse startCase(CreateLifeCaseRequest request) {
        UUID trackerId = UUID.randomUUID();
        try {
            // Phase 1: @Transactional - validate, track, build context
            Map<String, Object> initialContext = prepareAndTrack(trackerId, request);

            // Phase 2: no transaction - start the case and wait for caseId
            CaseHub caseHub = resolve(request.caseType());
            UUID caseId = caseHub.startCase(initialContext).toCompletableFuture().join();

            // Phase 3: @Transactional - persist caseId, signal into context
            persistCaseId(trackerId, caseId);
            caseHubRuntime.signal(caseId, "caseId", caseId.toString());

            return new LifeCaseResponse(caseId, request.caseType(), LifeCaseStatus.ACTIVE);
        } catch (Exception e) {
            LOG.errorf(e, "Case start failed for type=%s tracker=%s", request.caseType(), trackerId);
            try {
                markFailed(trackerId);
            } catch (Exception mfe) {
                LOG.errorf(mfe, "markFailed also failed for tracker=%s", trackerId);
            }
            throw new RuntimeException("Case start failed: " + e.getMessage(), e);
        }
    }

    @Transactional
    Map<String, Object> prepareAndTrack(UUID trackerId, CreateLifeCaseRequest request) {
        LifeCaseTracker tracker = new LifeCaseTracker();
        tracker.id = trackerId;
        tracker.caseType = request.caseType().caseName();
        tracker.status = LifeCaseStatus.ACTIVE;
        tracker.persist();

        Map<String, Object> ctx = new HashMap<>(request.context());
        ctx.put("lifeCaseType", request.caseType().caseName());
        return ctx;
    }

    @Transactional
    void persistCaseId(UUID trackerId, UUID caseId) {
        LifeCaseTracker tracker = LifeCaseTracker.findById(trackerId);
        if (tracker != null) {
            tracker.engineCaseId = caseId;
        }
    }

    @Transactional
    void markFailed(UUID trackerId) {
        LifeCaseTracker tracker = LifeCaseTracker.findById(trackerId);
        if (tracker != null) {
            tracker.status = LifeCaseStatus.FAILED;
            tracker.completedAt = Instant.now();
        }
    }

    private CaseHub resolve(LifeCaseType type) {
        return caseHubs.stream()
                .filter(hub -> hub.lifeCaseType() == type)
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "No CaseHub registered for type: " + type));
    }
}
