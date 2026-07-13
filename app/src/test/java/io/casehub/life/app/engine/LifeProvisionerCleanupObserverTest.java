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

import io.casehub.engine.common.spi.event.CaseLifecycleEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.mockito.Mockito.*;

class LifeProvisionerCleanupObserverTest {

    private LifeReactiveWorkerProvisioner mockProvisioner;
    private LifeProvisionerCleanupObserver observer;

    @BeforeEach
    void setup() {
        mockProvisioner = mock(LifeReactiveWorkerProvisioner.class);
        observer = new LifeProvisionerCleanupObserver();
        observer.provisioner = mockProvisioner;
    }

    @ParameterizedTest
    @ValueSource(strings = {"CaseCompleted", "CaseFaulted", "CaseCancelled"})
    void terminatesOnTerminalEvents(String eventType) {
        UUID caseId = UUID.randomUUID();
        CaseLifecycleEvent event = CaseLifecycleEvent.of(
                caseId, "test-tenant", "command", eventType,
                "COMPLETED", null, "System", null);

        observer.onCaseTerminal(event);

        verify(mockProvisioner).terminateAllForCase(caseId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CaseStarted", "CaseSuspended", "CaseResumed", "WorkSubmitted"})
    void ignoresNonTerminalEvents(String eventType) {
        UUID caseId = UUID.randomUUID();
        CaseLifecycleEvent event = CaseLifecycleEvent.of(
                caseId, "test-tenant", "command", eventType,
                "ACTIVE", null, "System", null);

        observer.onCaseTerminal(event);

        verify(mockProvisioner, never()).terminateAllForCase(any());
    }
}
