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

import io.casehub.api.model.CaseDefinition;
import io.casehub.life.api.LifeCaseType;
import io.casehub.life.app.engine.agent.CarePlanResult;
import io.casehub.life.app.engine.agent.HealthCheckResult;
import io.casehub.life.app.engine.agent.NeedsAssessmentResult;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Care coordination case hub — loads the YAML definition and augments it with
 * in-process worker functions.
 *
 * <p>The three humanTask bindings (assign-carer, escalate-concern, care-review) are
 * defined in YAML and handled by {@link io.casehub.workadapter.HumanTaskScheduleHandler}
 * — no Java worker needed.
 *
 * <p>SubCase pattern: the care-episode binding spawns a child case (care-episode) and
 * waits for completion. The child's final context is merged back as {@code episodeResult}.
 *
 * <p>Adaptive escalation: the escalate-concern binding fires only when
 * {@code .healthCheck.healthConcern == true} — otherwise the workflow proceeds
 * directly to care-review. In production, the escalation worker would also signal
 * an active appointment-cycle case via CaseHubRuntime.signal(). Refs casehub-life#6.
 */
@ApplicationScoped
public class CareCoordinationCaseHub extends LifeTypedCaseHub {

    public CareCoordinationCaseHub() {
        super("life/care-coordination.yaml", LifeAgent.HEALTH);
    }

    @Override
    public LifeCaseType lifeCaseType() {
        return LifeCaseType.CARE_COORDINATION;
    }

    @Override
    protected void configureCase(CaseDefinition definition) {
        definition.getWorkers().add(agentWorker("needs-assessment", """
                You are a care coordination agent. Assess care needs for the patient,
                determining care level, recommended frequency, and any special requirements.""", NeedsAssessmentResult.class));
        definition.getWorkers().add(agentWorker("care-plan", """
                You are a care coordination agent. Create a care plan with schedule,
                duration, and task list based on the needs assessment.""", CarePlanResult.class));
        definition.getWorkers().add(agentWorker("health-check", """
                You are a care coordination agent. Perform a periodic health check,
                reviewing the patient's condition and flagging any concerns.""", HealthCheckResult.class));
    }
}
