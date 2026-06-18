package io.casehub.life.app.engine.agent;

import dev.langchain4j.model.chat.ChatModel;
import io.casehub.api.model.ai.AgentException;
import io.casehub.api.model.ai.ChatModelProvider;
import io.casehub.api.model.ai.ModelType;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.lang.reflect.InvocationTargetException;
import java.time.Duration;

/**
 * ChatModelProvider targeting OpenClaw's OpenAI-compatible API (/v1/chat/completions).
 *
 * <p><b>Temporary placement.</b> Pending casehubio/engine#527 (add optional baseUrl to
 * OpenAiChatModelProvider in engine-api). Once that lands, delete this class and replace
 * callers with OpenAiChatModelProvider.builder().baseUrl(...).modelName("openclaw").build().
 *
 * <p>Uses reflection to set baseUrl on OpenAiChatModel.builder(), mirroring the pattern
 * in OpenAiChatModelProvider. chatModelProvider.get() is called exactly once during
 * Agent.build() — the resulting ChatModel is stored in the Agent for the JVM lifetime.
 * Config changes (api-url, timeout-seconds) require a restart.
 *
 * <p>GE-20260614-328420: model="openclaw" is required. Using an upstream provider model
 * ID is rejected by OpenClaw's /v1/chat/completions endpoint.
 */
@ApplicationScoped
public class LifeOpenClawChatModelProvider implements ChatModelProvider {

    @ConfigProperty(name = "casehub.life.openclaw.api-url")
    String apiUrl;

    @ConfigProperty(name = "casehub.life.openclaw.api-key", defaultValue = "no-key")
    String apiKey;

    @ConfigProperty(name = "casehub.life.openclaw.timeout-seconds", defaultValue = "120")
    int timeoutSeconds;

    @Override
    public ModelType type() {
        return ModelType.OPENAI;
    }

    @Override
    public ChatModel get() {
        try {
            final Class<?> clazz = Class.forName("dev.langchain4j.model.openai.OpenAiChatModel");
            final Object builder = clazz.getMethod("builder").invoke(null);
            final Class<?> bc = builder.getClass();
            invoke(bc, builder, "baseUrl", String.class, apiUrl);
            invoke(bc, builder, "apiKey", String.class, apiKey);
            invoke(bc, builder, "modelName", String.class, "openclaw"); // GE-20260614-328420
            invoke(bc, builder, "timeout", Duration.class, Duration.ofSeconds(timeoutSeconds));
            return (ChatModel) bc.getMethod("build").invoke(builder);
        } catch (final InvocationTargetException e) {
            final Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new AgentException("Failed to build OpenClawChatModel: " + cause.getMessage(), cause);
        } catch (final Exception e) {
            throw new AgentException("Failed to build OpenClawChatModel: " + e.getMessage(), e);
        }
    }

    private static void invoke(
            final Class<?> builderClass,
            final Object builder,
            final String method,
            final Class<?> paramType,
            final Object value) throws Exception {
        builderClass.getMethod(method, paramType).invoke(builder, value);
    }
}
