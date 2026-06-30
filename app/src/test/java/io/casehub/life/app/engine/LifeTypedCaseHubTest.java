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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.model.chat.ChatModel;
import io.casehub.api.model.AgentWorkerFunction;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.ai.ChatModelProvider;
import io.casehub.life.api.LifeCaseType;
import io.casehub.life.app.engine.agent.LifeAgentDescriptorFactory;
import io.casehub.life.app.engine.agent.LifeOpenClawChatModelFactory;
import io.casehub.worker.api.Worker;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LifeTypedCaseHubTest {

    private LifeOpenClawChatModelFactory mockFactory;
    private LifeAgentDescriptorFactory mockDescriptorFactory;

    @BeforeEach
    void setUp() {
        mockFactory = mock(LifeOpenClawChatModelFactory.class);
        var mockProvider = mock(ChatModelProvider.class);
        when(mockProvider.get()).thenReturn(mock(ChatModel.class));
        when(mockFactory.forAgent(any())).thenReturn(mockProvider);
        mockDescriptorFactory = mock(LifeAgentDescriptorFactory.class);
        when(mockDescriptorFactory.descriptorFor(any()))
                .thenReturn(mock(io.casehub.eidos.api.AgentDescriptor.class));
    }

    @Test
    void agentWorkerProducesCorrectNameAndCapability() {
        var hub = createHub();
        Worker worker = hub.agentWorker("test-cap", "Test system prompt", Map.class);
        assertThat(worker.name()).isEqualTo("test-cap-agent");
        assertThat(worker.capabilityNames()).containsExactly("test-cap");
        assertThat(worker.function()).isInstanceOf(AgentWorkerFunction.class);
    }

    @Test
    void lifeCaseTypeReturnsExpectedValue() {
        var hub = createHub();
        assertThat(hub.lifeCaseType()).isEqualTo(LifeCaseType.APPOINTMENT_CYCLE);
    }

    @Test
    void agentGetterReturnsConstructorValue() {
        var hub = createHub();
        assertThat(hub.agent()).isEqualTo(LifeAgent.HEALTH);
    }

    @Test
    void augmentCallsConfigureCaseThenRegistersDescriptors() {
        var hub = createHub();
        var definition = mock(CaseDefinition.class);
        when(definition.getWorkers()).thenReturn(new java.util.ArrayList<>());
        hub.augment(definition);

        assertThat(hub.configureCaseCalled).isTrue();
        verify(mockDescriptorFactory).descriptorFor(LifeAgent.HEALTH);
        verify(definition).setAgentDescriptors(any());
    }

    @Test
    void augmentProducesWorkersAndDescriptorsTogether() {
        var hub = createHubWithWorker();
        var workers = new java.util.ArrayList<Worker>();
        var definition = mock(CaseDefinition.class);
        when(definition.getWorkers()).thenReturn(workers);
        hub.augment(definition);

        assertThat(workers).hasSize(1);
        assertThat(workers.get(0).name()).isEqualTo("test-cap-agent");
        verify(definition).setAgentDescriptors(any());
    }

    @Test
    void agentPassedToFactories() {
        var hub = createHub();
        var definition = mock(CaseDefinition.class);
        when(definition.getWorkers()).thenReturn(new java.util.ArrayList<>());
        hub.augment(definition);

        verify(mockFactory, never()).forAgent(any());
        verify(mockDescriptorFactory).descriptorFor(LifeAgent.HEALTH);
    }

    private TestCaseHub createHub() {
        var hub = new TestCaseHub();
        hub.openClawFactory = mockFactory;
        hub.descriptorFactory = mockDescriptorFactory;
        return hub;
    }

    private TestCaseHubWithWorker createHubWithWorker() {
        var hub = new TestCaseHubWithWorker();
        hub.openClawFactory = mockFactory;
        hub.descriptorFactory = mockDescriptorFactory;
        return hub;
    }

    static class TestCaseHub extends LifeTypedCaseHub {
        boolean configureCaseCalled = false;

        TestCaseHub() {
            super("life/appointment-cycle.yaml", LifeAgent.HEALTH);
        }

        @Override
        public LifeCaseType lifeCaseType() {
            return LifeCaseType.APPOINTMENT_CYCLE;
        }

        @Override
        protected void configureCase(CaseDefinition definition) {
            configureCaseCalled = true;
        }
    }

    static class TestCaseHubWithWorker extends LifeTypedCaseHub {
        TestCaseHubWithWorker() {
            super("life/appointment-cycle.yaml", LifeAgent.HEALTH);
        }

        @Override
        public LifeCaseType lifeCaseType() {
            return LifeCaseType.APPOINTMENT_CYCLE;
        }

        @Override
        protected void configureCase(CaseDefinition definition) {
            definition.getWorkers().add(agentWorker("test-cap", "Test prompt", Map.class));
        }
    }
}
