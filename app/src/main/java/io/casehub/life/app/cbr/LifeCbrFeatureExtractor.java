package io.casehub.life.app.cbr;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.api.model.cbr.CbrConfig;
import io.casehub.api.model.cbr.JqFeatureExtractor;
import io.casehub.engine.common.spi.CaseDefinitionRegistry;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.platform.expression.JQEvaluator;
import io.casehub.platform.expression.ValidationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class LifeCbrFeatureExtractor {

    public record ExtractionResult(CbrConfig config, Map<String, FeatureValue> features) {}

    private final CaseDefinitionRegistry registry;
    private final JQEvaluator jqEvaluator;

    @Inject
    public LifeCbrFeatureExtractor(CaseDefinitionRegistry registry, JQEvaluator jqEvaluator) {
        this.registry = registry;
        this.jqEvaluator = jqEvaluator;
    }

    public Optional<ExtractionResult> extract(String caseType, JsonNode context) {
        var definition = registry.findByName(caseType).orElse(null);
        if (definition == null || definition.getCbrConfig() == null) return Optional.empty();

        CbrConfig config = definition.getCbrConfig();
        if (!(config.featureExtractor() instanceof JqFeatureExtractor jq)) {
            return Optional.empty();
        }

        Map<String, Object> rawFeatures = new LinkedHashMap<>();
        for (var entry : jq.featureExpressions().entrySet()) {
            ValidationResult result = jqEvaluator.eval(entry.getValue(), context);
            if (!result.ok() || result.output().isEmpty()) continue;
            JsonNode node = result.output().get(0);
            if (node.isNull()) continue;
            rawFeatures.put(entry.getKey(), unwrap(node));
        }

        return Optional.of(new ExtractionResult(config, FeatureValue.toFeatureMap(rawFeatures)));
    }

    static Object unwrap(JsonNode node) {
        if (node.isTextual()) return node.asText();
        if (node.isInt()) return node.asInt();
        if (node.isLong()) return node.asLong();
        if (node.isDouble() || node.isFloat()) return node.asDouble();
        if (node.isBoolean()) return node.asBoolean();
        return node.asText();
    }
}
