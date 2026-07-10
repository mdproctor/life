package io.casehub.life.app.cbr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.api.model.cbr.CbrConfig;
import io.casehub.api.model.cbr.JqFeatureExtractor;
import io.casehub.api.spi.CaseOutcomeEvent;
import io.casehub.api.spi.CaseOutcomeObserver;
import io.casehub.engine.common.spi.CaseDefinitionRegistry;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.platform.expression.JQEvaluator;
import io.casehub.platform.expression.ValidationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class LifeCaseOutcomeCbrWriter implements CaseOutcomeObserver {

    static final String TENANT_ID = "life-personal";

    private static final Logger LOG = Logger.getLogger(LifeCaseOutcomeCbrWriter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CbrCaseMemoryStore cbrStore;
    private final CaseDefinitionRegistry registry;
    private final JQEvaluator jqEvaluator;
    private final Map<String, LifeCbrDescriptionProvider> providers;

    @Inject
    public LifeCaseOutcomeCbrWriter(CbrCaseMemoryStore cbrStore,
                                    CaseDefinitionRegistry registry,
                                    JQEvaluator jqEvaluator,
                                    Instance<LifeCbrDescriptionProvider> providers) {
        this.cbrStore = cbrStore;
        this.registry = registry;
        this.jqEvaluator = jqEvaluator;
        this.providers = new HashMap<>();
        providers.stream().forEach(p -> this.providers.put(p.caseType(), p));
    }

    @Override
    public void onOutcome(CaseOutcomeEvent event) {
        LifeCbrDescriptionProvider descProvider = providers.get(event.caseType());
        if (descProvider == null) return;

        try {
            var definition = registry.findByName(event.caseType()).orElse(null);
            if (definition == null || definition.getCbrConfig() == null) return;

            CbrConfig config = definition.getCbrConfig();
            if (!(config.featureExtractor() instanceof JqFeatureExtractor jq)) {
                LOG.warnf("CBR retention skipped for %s — lambda extractor unsupported at retention time",
                        event.caseType());
                return;
            }

            JsonNode jsonNode = MAPPER.valueToTree(event.caseFileSnapshot());
            Map<String, Object> features = extractFeatures(jq, jsonNode);

            PlanCbrCase cbrCase = new PlanCbrCase(
                    descProvider.describeProblem(event.caseFileSnapshot()),
                    descProvider.describeSolution(event.caseFileSnapshot()),
                    event.outcomeLabel(),
                    null,
                    features,
                    List.of());

            cbrStore.store(
                    cbrCase,
                    event.caseType(),
                    descProvider.extractEntityId(event.caseFileSnapshot(), event.caseId()),
                    new MemoryDomain(config.domain()),
                    TENANT_ID,
                    event.caseId().toString());

        } catch (Exception e) {
            LOG.warnf(e, "CBR retention failed for case %s (%s) — proceeding without recording",
                    event.caseId(), event.caseType());
        }
    }

    private Map<String, Object> extractFeatures(JqFeatureExtractor jq, JsonNode input) {
        Map<String, Object> features = new LinkedHashMap<>();
        for (var entry : jq.featureExpressions().entrySet()) {
            ValidationResult result = jqEvaluator.eval(entry.getValue(), input);
            if (!result.ok() || result.output().isEmpty()) continue;
            JsonNode node = result.output().get(0);
            if (node.isNull()) continue;
            features.put(entry.getKey(), unwrap(node));
        }
        return features;
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
