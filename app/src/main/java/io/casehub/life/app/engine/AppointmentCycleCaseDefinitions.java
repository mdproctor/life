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

import io.casehub.api.model.Binding;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.ContextChangeTrigger;
import io.casehub.api.model.Goal;
import io.casehub.api.model.GoalExpression;
import io.casehub.api.model.GoalKind;
import io.casehub.api.model.HumanTaskTarget;
import io.casehub.worker.api.Capability;

import java.time.Duration;
import java.util.Set;

/**
 * Fluent Java DSL companion for the appointment-cycle case definition.
 *
 * <p>Produces the same CaseDefinition as the YAML at
 * {@code life/appointment-cycle.yaml} but via the Java builder API. JQ string
 * expressions match the YAML — no lambdas.
 *
 * <p>Useful for programmatic construction in tests or when the YAML parser is
 * not on the classpath.
 */
public final class AppointmentCycleCaseDefinitions {

    private AppointmentCycleCaseDefinitions() {}

    public static CaseDefinition appointmentCycle() {
        Capability bookAppointment = cap("book-appointment",
                "Book an appointment with the specified provider",
                "{ appointmentType: .appointmentType, provider: .provider }",
                "{ booking: . }");

        Capability findAlternative = cap("find-alternative",
                "Find an alternative provider after a booking decline",
                "{ appointmentType: .appointmentType, declinedProvider: .provider }",
                "{ booking: . }");

        Capability confirmAppointment = cap("confirm-appointment",
                "Send appointment confirmation and reminder",
                "{ booking: .booking }",
                "{ confirmation: . }");

        Capability preVisitPrep = cap("pre-visit-prep",
                "Send pre-visit checklist and preparation instructions",
                "{ booking: .booking, confirmation: .confirmation }",
                "{ prep: . }");

        Capability recordHealthDecision = cap("record-health-decision",
                "Record health decision to tamper-evident ledger",
                "{ visitNotes: .visitNotes, booking: .booking }",
                "{ healthDecisionRecorded: . }");

        Goal appointmentComplete = Goal.builder()
                .name("appointment-complete")
                .kind(GoalKind.SUCCESS)
                .condition(".healthDecisionRecorded != null")
                .build();

        return CaseDefinition.builder()
                .namespace("casehub-life")
                .name("appointment-cycle")
                .version("1.0.0")
                .title("Appointment cycle — book, confirm, prep, attend, record")
                .capabilities(bookAppointment, findAlternative, confirmAppointment,
                        preVisitPrep, recordHealthDecision)
                .goals(appointmentComplete)
                .completion(GoalExpression.allOf(appointmentComplete))
                .bindings(
                        Binding.builder()
                                .name("book-appointment")
                                .on(new ContextChangeTrigger("."))
                                .when(".appointmentType != null and .booking == null")
                                .capability(bookAppointment)
                                .build(),
                        Binding.builder()
                                .name("find-alternative")
                                .on(new ContextChangeTrigger("."))
                                .when(".booking != null and .booking.declined == true and .booking.alternativeFound == null")
                                .capability(findAlternative)
                                .build(),
                        Binding.builder()
                                .name("confirm-appointment")
                                .on(new ContextChangeTrigger("."))
                                .when(".booking != null and .booking.declined != true and .confirmation == null")
                                .capability(confirmAppointment)
                                .build(),
                        Binding.builder()
                                .name("pre-visit-prep")
                                .on(new ContextChangeTrigger("."))
                                .when(".confirmation != null and .prep == null")
                                .capability(preVisitPrep)
                                .build(),
                        Binding.builder()
                                .name("attend-and-record")
                                .on(new ContextChangeTrigger("."))
                                .when(".prep != null and .visitNotes == null")
                                .humanTask(HumanTaskTarget.inline()
                                        .title("Record post-visit notes")
                                        .expiresIn(Duration.ofHours(48))
                                        .candidateGroups(Set.of("household-member"))
                                        .scope("casehubio/life/health")
                                        .inputMapping("{ booking: .booking, prep: .prep }")
                                        .outputMapping("{ visitNotes: . }")
                                        .build())
                                .build(),
                        Binding.builder()
                                .name("record-health-decision")
                                .on(new ContextChangeTrigger("."))
                                .when(".visitNotes != null and .healthDecisionRecorded == null")
                                .capability(recordHealthDecision)
                                .build()
                )
                .build();
    }

    private static Capability cap(String name, String description,
                                  String inputSchema, String outputSchema) {
        return Capability.builder()
                .name(name)
                .description(description)
                .inputSchema(inputSchema)
                .outputSchema(outputSchema)
                .build();
    }
}
