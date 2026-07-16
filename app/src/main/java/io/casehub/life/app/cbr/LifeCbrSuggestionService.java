package io.casehub.life.app.cbr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.api.model.cbr.CbrConfig;
import io.casehub.life.api.CbrSuggestions;
import io.casehub.life.api.FeatureStatistics;
import io.casehub.life.api.LifeCaseType;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class LifeCbrSuggestionService {

    private static final Logger LOG = Logger.getLogger(LifeCbrSuggestionService.class);
    private static final String TENANT_ID = "life-personal";

    private final LifeCbrFeatureExtractor featureExtractor;
    private final CbrCaseMemoryStore cbrStore;
    private final ObjectMapper objectMapper;

    @Inject
    public LifeCbrSuggestionService(LifeCbrFeatureExtractor featureExtractor,
                                     CbrCaseMemoryStore cbrStore,
                                     ObjectMapper objectMapper) {
        this.featureExtractor = featureExtractor;
        this.cbrStore = cbrStore;
        this.objectMapper = objectMapper;
    }

    public CbrSuggestions suggest(LifeCaseType caseType, Map<String, Object> initialContext) {
        try {
            JsonNode contextNode = objectMapper.valueToTree(initialContext);
            var extraction = featureExtractor.extract(caseType.caseName(), contextNode);
            if (extraction.isEmpty()) return CbrSuggestions.EMPTY;

            var result = extraction.get();
            CbrConfig config = result.config();

            CbrQuery query = CbrQuery.of(
                    TENANT_ID,
                    new MemoryDomain(config.domain()),
                    caseType.caseName(),
                    result.features(),
                    config.topK())
                .withWeights(config.weights())
                .withMinSimilarity(config.minSimilarity())
                .withVectorWeight(config.vectorWeight());

            List<ScoredCbrCase<PlanCbrCase>> cases = cbrStore.retrieveSimilar(query, PlanCbrCase.class);
            if (cases.size() < 2) return CbrSuggestions.EMPTY;

            Map<String, FeatureStatistics> featureStats = computeFeatureStats(cases);
            double successRate = computeSuccessRate(cases);
            double avgSimilarity = cases.stream().mapToDouble(ScoredCbrCase::score).average().orElse(0.0);

            return new CbrSuggestions(featureStats, successRate, cases.size(), avgSimilarity);
        } catch (Exception e) {
            LOG.warnf(e, "CBR suggestion failed for %s — returning empty", caseType);
            return CbrSuggestions.EMPTY;
        }
    }

    public LifeCbrRetrievalResult retrieveForAdaptation(LifeCaseType caseType,
                                                        Map<String, Object> initialContext) {
        try {
            JsonNode contextNode = objectMapper.valueToTree(initialContext);
            var      extraction  = featureExtractor.extract(caseType.caseName(), contextNode);
            if (extraction.isEmpty()) {return LifeCbrRetrievalResult.EMPTY;}

            var       result = extraction.get();
            CbrConfig config = result.config();

            CbrQuery query = CbrQuery.of(
                                             TENANT_ID,
                                             new MemoryDomain(config.domain()),
                                             caseType.caseName(),
                                             result.features(),
                                             config.topK())
                                     .withWeights(config.weights())
                                     .withMinSimilarity(config.minSimilarity())
                                     .withVectorWeight(config.vectorWeight());

            List<ScoredCbrCase<PlanCbrCase>> cases = cbrStore.retrieveSimilar(query, PlanCbrCase.class);
            if (cases.isEmpty()) {return LifeCbrRetrievalResult.EMPTY;}

            CbrSuggestions suggestions = CbrSuggestions.EMPTY;
            if (cases.size() >= 2) {
                Map<String, FeatureStatistics> featureStats  = computeFeatureStats(cases);
                double                         successRate   = computeSuccessRate(cases);
                double                         avgSimilarity = cases.stream().mapToDouble(ScoredCbrCase::score).average().orElse(0.0);
                suggestions = new CbrSuggestions(featureStats, successRate, cases.size(), avgSimilarity);
            }

            return new LifeCbrRetrievalResult(suggestions, cases, result.features());
        } catch (Exception e) {
            LOG.warnf(e, "CBR retrieval for adaptation failed for %s — returning empty", caseType);
            return LifeCbrRetrievalResult.EMPTY;
        }
    }


    private Map<String, FeatureStatistics> computeFeatureStats(List<ScoredCbrCase<PlanCbrCase>> cases) {
        Map<String, List<Double>> numericValues = new LinkedHashMap<>();
        for (var scored : cases) {
            for (var entry : scored.cbrCase().features().entrySet()) {
                if (entry.getValue() instanceof FeatureValue.NumberVal num) {
                    numericValues.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                            .add(num.value());
                }
            }
        }

        Map<String, FeatureStatistics> stats = new LinkedHashMap<>();
        for (var entry : numericValues.entrySet()) {
            double[] values = entry.getValue().stream().mapToDouble(Double::doubleValue).toArray();
            stats.put(entry.getKey(), FeatureStatistics.compute(values));
        }
        return stats;
    }

    private double computeSuccessRate(List<ScoredCbrCase<PlanCbrCase>> cases) {
        long completed = cases.stream()
                .filter(c -> "COMPLETED".equals(c.cbrCase().outcome()))
                .count();
        return (double) completed / cases.size();
    }
}
