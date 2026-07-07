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

import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.api.model.ai.ChatModelProvider;
import io.casehub.api.model.ai.ModelType;
import io.casehub.life.app.engine.agent.LifeOpenClawChatModelFactory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LifeHeartbeatJobTest {

    private LifeOpenClawChatModelFactory mockFactory;
    private CaseHubRuntime mockRuntime;
    private LifeChannelContextProvider mockChannelProvider;
    private LifeHeartbeatJob job;
    private final UUID caseId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        mockFactory = mock(LifeOpenClawChatModelFactory.class);
        mockRuntime = mock(CaseHubRuntime.class);

        ChatModel mockModel = mock(ChatModel.class);
        AiMessage aiMessage = AiMessage.from(
                "{\"progressPercent\":75,\"status\":\"on-track\","
                + "\"concerns\":null,\"recommendedAction\":null,\"escalationRequired\":false}");
        ChatResponse response = ChatResponse.builder().aiMessage(aiMessage).build();
        when(mockModel.chat(any(ChatRequest.class))).thenReturn(response);

        ChatModelProvider provider = new ChatModelProvider() {
            @Override public ModelType type() { return ModelType.OPENAI; }
            @Override public ChatModel get() { return mockModel; }
        };
        when(mockFactory.forAgent(any(LifeAgent.class))).thenReturn(provider);

        when(mockRuntime.query(any(UUID.class), eq(".")))
                .thenReturn(CompletableFuture.completedFuture(
                        Map.of("contractorRequest", Map.of("contractor", "AcmeBuild"))));
        when(mockRuntime.signal(any(UUID.class), any(String.class), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        mockChannelProvider = mock(LifeChannelContextProvider.class);
        when(mockChannelProvider.gatherContext(any(UUID.class)))
                .thenReturn(Map.of("channelContext", Map.of()));

        job = new LifeHeartbeatJob();
        job.openClawFactory = mockFactory;
        job.caseHubRuntime = mockRuntime;
        job.channelContextProvider = mockChannelProvider;
    }

    @Test
    void executesAgentAndSignalsResult() throws Exception {
        JobExecutionContext ctx = mock(JobExecutionContext.class);
        JobDataMap data = new JobDataMap();
        data.put("agent", "HOME");
        data.put("caseId", caseId.toString());
        data.put("capabilityName", "contractor-sentinel");
        when(ctx.getMergedJobDataMap()).thenReturn(data);

        job.execute(ctx);

        verify(mockRuntime).query(caseId, ".");
        verify(mockFactory).forAgent(LifeAgent.HOME);
        verify(mockRuntime).signal(eq(caseId), eq("sentinelReport"), any(Map.class));
    }

    @Test
    void signalContainsSentinelReportData() throws Exception {
        JobExecutionContext ctx = mock(JobExecutionContext.class);
        JobDataMap data = new JobDataMap();
        data.put("agent", "HOME");
        data.put("caseId", caseId.toString());
        data.put("capabilityName", "contractor-sentinel");
        when(ctx.getMergedJobDataMap()).thenReturn(data);

        job.execute(ctx);

        var captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(mockRuntime).signal(eq(caseId), eq("sentinelReport"), captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> report = (Map<String, Object>) captor.getValue();
        assertThat(report).containsKey("progressPercent");
        assertThat(report.get("escalationRequired")).isEqualTo(false);
    }

    @Test
    void executeMergesChannelContextIntoCaseContext() throws Exception {
        when(mockChannelProvider.gatherContext(caseId)).thenReturn(
                Map.of("channelContext", Map.of("delegation", List.of(
                        Map.of("sender", "finance-agent", "type", "STATUS",
                                "content", "Budget warning", "createdAt", "2026-07-07T10:00:00Z")))));

        JobExecutionContext ctx = mock(JobExecutionContext.class);
        JobDataMap data = new JobDataMap();
        data.put("agent", "HOME");
        data.put("caseId", caseId.toString());
        data.put("capabilityName", "contractor-sentinel");
        when(ctx.getMergedJobDataMap()).thenReturn(data);

        job.execute(ctx);

        verify(mockChannelProvider).gatherContext(caseId);
        verify(mockRuntime).signal(eq(caseId), eq("sentinelReport"), any(Map.class));
    }

    @Test
    void executeCompletesWhenChannelContextFails() throws Exception {
        when(mockChannelProvider.gatherContext(any(UUID.class)))
                .thenThrow(new RuntimeException("Channel DB unavailable"));

        JobExecutionContext ctx = mock(JobExecutionContext.class);
        JobDataMap data = new JobDataMap();
        data.put("agent", "HOME");
        data.put("caseId", caseId.toString());
        data.put("capabilityName", "contractor-sentinel");
        when(ctx.getMergedJobDataMap()).thenReturn(data);

        job.execute(ctx);

        verify(mockRuntime).signal(eq(caseId), eq("sentinelReport"), any(Map.class));
    }
}
