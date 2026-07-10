package io.casehub.life.app.cbr;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.api.model.cbr.CbrConfig;
import io.casehub.api.model.cbr.JqFeatureExtractor;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.api.spi.routing.RoutingOutcome;
import io.casehub.api.spi.routing.RoutingOutcomeRecorder;
import io.casehub.engine.common.spi.CaseDefinitionRegistry;
import io.casehub.life.app.entity.LifeCaseTracker;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.neocortex.memory.cbr.PlanTrace;
import io.casehub.platform.expression.JQEvaluator;
import io.casehub.platform.expression.ValidationResult;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.annotation.Nullable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class LifeRoutingOutcomeRecorder implements RoutingOutcomeRecorder {

    private static final Logger LOG = Logger.getLogger(LifeRoutingOutcomeRecorder.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final CbrCaseMemoryStore cbrStore;
    private final CaseDefinitionRegistry registry;
    private final JQEvaluator jqEvaluator;
    private final CaseTypeLookup caseTypeLookup;
    private final Map<String, LifeCbrDescriptionProvider> providers;

    @Inject
    public LifeRoutingOutcomeRecorder(CbrCaseMemoryStore cbrStore,
                                       CaseDefinitionRegistry registry,
                                       JQEvaluator jqEvaluator,
                                       CaseTypeLookup caseTypeLookup,
                                       Instance<LifeCbrDescriptionProvider> providers) {
        this.cbrStore = cbrStore;
        this.registry = registry;
        this.jqEvaluator = jqEvaluator;
        this.caseTypeLookup = caseTypeLookup;
        this.providers = new HashMap<>();
        providers.stream().forEach(p -> this.providers.put(p.caseType(), p));
    }

    @Override
    public Uni<Void> record(AgentRoutingContext context, String workerId, String bindingName,
                            RoutingOutcome outcome, @Nullable Duration executionDuration) {
        return Uni.createFrom().item(() -> {
            Optional<String> caseTypeOpt = caseTypeLookup.findCaseType(context.caseId());
            if (caseTypeOpt.isEmpty()) return null;
            String caseType = caseTypeOpt.get();

            LifeCbrDescriptionProvider descProvider = providers.get(caseType);
            if (descProvider == null) return null;

            var definition = registry.findByName(caseType).orElse(null);
            if (definition == null || definition.getCbrConfig() == null) return null;

            CbrConfig config = definition.getCbrConfig();
            if (!(config.featureExtractor() instanceof JqFeatureExtractor jq)) {
                LOG.warnf("CBR routing retention skipped for %s — lambda extractor unsupported", caseType);
                return null;
            }

            Map<String, Object> features = extractFeatures(jq, context.caseContext());
            Map<String, Object> caseData = MAPPER.convertValue(context.caseContext(), MAP_TYPE);

            PlanTrace trace = new PlanTrace(
                    bindingName, context.capabilityName(),
                    workerId, outcome.name(), 0, Map.of());

            PlanCbrCase cbrCase = new PlanCbrCase(
                    descProvider.describeProblem(caseData),
                    descProvider.describeSolution(caseData),
                    outcome.name(),
                    null,
                    features,
                    List.of(trace));

            cbrStore.store(
                    cbrCase,
                    caseType,
                    "agent-routing",
                    new MemoryDomain(config.domain()),
                    context.tenancyId(),
                    context.caseId().toString());

            return null;
        })
        .emitOn(Infrastructure.getDefaultWorkerPool())
        .onFailure().recoverWithItem(failure -> {
            LOG.warnf(failure, "CBR routing retention failed — proceeding without recording");
            return null;
        })
        .replaceWithVoid();
    }

    private Map<String, Object> extractFeatures(JqFeatureExtractor jq, JsonNode input) {
        Map<String, Object> features = new LinkedHashMap<>();
        for (var entry : jq.featureExpressions().entrySet()) {
            ValidationResult result = jqEvaluator.eval(entry.getValue(), input);
            if (!result.ok() || result.output().isEmpty()) continue;
            JsonNode node = result.output().get(0);
            if (node.isNull()) continue;
            features.put(entry.getKey(), LifeCaseOutcomeCbrWriter.unwrap(node));
        }
        return features;
    }

    @ApplicationScoped
    public static class CaseTypeLookup {
        public Optional<String> findCaseType(UUID engineCaseId) {
            return LifeCaseTracker.findByEngineCaseId(engineCaseId)
                    .map(t -> t.caseType);
        }
    }
}
