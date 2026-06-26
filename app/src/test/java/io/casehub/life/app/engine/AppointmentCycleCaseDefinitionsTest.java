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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.GoalKind;
import io.casehub.api.model.HumanTaskTarget;
import org.junit.jupiter.api.Test;
import io.casehub.api.model.evaluator.ListEvaluator;

/**
 * Verifies the fluent DSL companion produces a valid CaseDefinition with the
 * correct structure — name, namespace, binding count, goal count, humanTask.
 *
 * <p>This is a pure unit test — no Quarkus container needed.
 */
class AppointmentCycleCaseDefinitionsTest {

    @Test
    void definitionHasCorrectIdentity() {
        CaseDefinition def = AppointmentCycleCaseDefinitions.appointmentCycle();
        assertNotNull(def);
        assertEquals("casehub-life", def.getNamespace());
        assertEquals("appointment-cycle", def.getName());
        assertEquals("1.0.0", def.getVersion());
    }

    @Test
    void hasFiveCapabilities() {
        CaseDefinition def = AppointmentCycleCaseDefinitions.appointmentCycle();
        assertEquals(5, def.getCapabilities().size());
        var names = def.getCapabilities().stream().map(c -> c.name()).toList();
        assertTrue(names.contains("book-appointment"));
        assertTrue(names.contains("find-alternative"));
        assertTrue(names.contains("confirm-appointment"));
        assertTrue(names.contains("pre-visit-prep"));
        assertTrue(names.contains("record-health-decision"));
    }

    @Test
    void hasSixBindings() {
        CaseDefinition def = AppointmentCycleCaseDefinitions.appointmentCycle();
        assertEquals(6, def.getBindings().size());
        var names = def.getBindings().stream().map(b -> b.getName()).toList();
        assertTrue(names.contains("book-appointment"));
        assertTrue(names.contains("find-alternative"));
        assertTrue(names.contains("confirm-appointment"));
        assertTrue(names.contains("pre-visit-prep"));
        assertTrue(names.contains("attend-and-record"));
        assertTrue(names.contains("record-health-decision"));
    }

    @Test
    void hasOneSuccessGoal() {
        CaseDefinition def = AppointmentCycleCaseDefinitions.appointmentCycle();
        assertEquals(1, def.getGoals().size());
        assertEquals("appointment-complete", def.getGoals().get(0).getName());
        assertEquals(GoalKind.SUCCESS, def.getGoals().get(0).getKind());
    }

    @Test
    void hasCompletion() {
        CaseDefinition def = AppointmentCycleCaseDefinitions.appointmentCycle();
        assertNotNull(def.getCompletion(), "Completion must be set");
    }

    @Test
    void attendAndRecordBindingIsHumanTask() {
        CaseDefinition def = AppointmentCycleCaseDefinitions.appointmentCycle();
        var binding = def.getBindings().stream()
                .filter(b -> "attend-and-record".equals(b.getName()))
                .findFirst()
                .orElseThrow();
        assertTrue(binding.target() instanceof HumanTaskTarget ht
                && "Record post-visit notes".equals(ht.title())
                && ht.candidateGroups() instanceof ListEvaluator.StaticList sl && sl.values().contains("household-member")
                && "casehubio/life/health".equals(ht.scope()));
    }
}
