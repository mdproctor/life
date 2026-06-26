package io.casehub.life.app.engine.agent;

import io.casehub.api.model.ai.ChatModelProvider;
import io.casehub.api.model.ai.ModelType;
import io.casehub.openclaw.casehub.DirectCallBridge;
import io.casehub.openclaw.casehub.OpenClawAgentProvider;
import io.casehub.openclaw.casehub.OpenClawChatModel;
import io.casehub.openclaw.client.OpenClawClientConfig;
import io.casehub.openclaw.client.OpenClawHookClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;

/**
 * Factory for creating ChatModelProviders backed by OpenClaw's /hooks/agent endpoint.
 *
 * <p>Each OpenClaw agent (health-agent, home-agent, finance-agent, etc.) is identified by
 * an agentId string. The factory creates an {@link OpenClawAgentProvider} wrapping the
 * {@link DirectCallBridge} and {@link OpenClawHookClient}, then wraps that in an
 * {@link OpenClawChatModel}, and returns it as a {@link ChatModelProvider}.
 *
 * <p>This replaces {@code LifeOpenClawChatModelProvider} which called /v1/chat/completions
 * synchronously with no skill access. The new factory uses /hooks/agent with real skills.
 *
 * <p>Config keys (from {@link OpenClawClientConfig}):
 * <ul>
 *   <li>{@code casehub.openclaw.gateway.url} — OpenClaw gateway base URL (required)</li>
 *   <li>{@code casehub.openclaw.gateway.bearer-token} — API key for /hooks/agent (required)</li>
 *   <li>{@code casehub.openclaw.delivery.base-url} — webhook callback URL (required)</li>
 *   <li>{@code casehub.openclaw.agent.default-timeout-seconds} — default 120</li>
 * </ul>
 *
 * <p>Refs casehubio/life#38 — Phase 2 migration from direct LLM calls to OpenClaw
 * AgentProvider with skill ecosystem access.
 */
@ApplicationScoped
public class LifeOpenClawChatModelFactory {

    private DirectCallBridge bridge;
    private OpenClawHookClient hookClient;
    private String deliveryBaseUrl;
    private int timeoutSeconds;

    @Inject
    public LifeOpenClawChatModelFactory(DirectCallBridge bridge,
                                         OpenClawHookClient hookClient,
                                         OpenClawClientConfig config) {
        this.bridge = bridge;
        this.hookClient = hookClient;
        this.deliveryBaseUrl = config.delivery().baseUrl();
        this.timeoutSeconds = config.agent().defaultTimeoutSeconds();
    }

    /** No-arg constructor for test subclasses that override forAgent() entirely. */
    protected LifeOpenClawChatModelFactory() {
        this.bridge = null;
        this.hookClient = null;
        this.deliveryBaseUrl = null;
        this.timeoutSeconds = 0;
    }

    /**
     * Creates a ChatModelProvider for the specified OpenClaw agent.
     *
     * @param openClawAgentId the agent identifier (e.g., "health-agent", "home-agent")
     * @return a ChatModelProvider backed by OpenClawAgentProvider → DirectCallBridge
     */
    public ChatModelProvider forAgent(String openClawAgentId) {
        var provider = new OpenClawAgentProvider(
                bridge, hookClient, openClawAgentId, deliveryBaseUrl);
        var chatModel = new OpenClawChatModel(
                provider, Duration.ofSeconds(timeoutSeconds));
        return new ChatModelProvider() {
            @Override
            public ModelType type() {
                return ModelType.OPENAI;
            }

            @Override
            public dev.langchain4j.model.chat.ChatModel get() {
                return chatModel;
            }
        };
    }
}
