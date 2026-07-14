package io.casehub.life.app.cbr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.cbr.CbrConfig;
import io.casehub.api.model.cbr.JqFeatureExtractor;
import io.casehub.api.model.cbr.LambdaFeatureExtractor;
import io.casehub.engine.common.spi.CaseDefinitionRegistry;
import io.casehub.platform.expression.JQEvaluator;
import io.casehub.platform.expression.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LifeCbrFeatureExtractorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CaseDefinitionRegistry registry;
    private JQEvaluator jqEvaluator;
    private LifeCbrFeatureExtractor extractor;

    @BeforeEach
    void setup() {
        registry = mock(CaseDefinitionRegistry.class);
        jqEvaluator = mock(JQEvaluator.class);
        extractor = new LifeCbrFeatureExtractor(registry, jqEvaluator);
    }

    @Test
    void extract_noDefinition_returnsEmpty() {
        when(registry.findByName("travel-plan")).thenReturn(Optional.empty());
        assertTrue(extractor.extract("travel-plan", MAPPER.createObjectNode()).isEmpty());
    }

    @Test
    void extract_noCbrConfig_returnsEmpty() {
        var def = mock(CaseDefinition.class);
        when(def.getCbrConfig()).thenReturn(null);
        when(registry.findByName("travel-plan")).thenReturn(Optional.of(def));
        assertTrue(extractor.extract("travel-plan", MAPPER.createObjectNode()).isEmpty());
    }

    @Test
    void extract_lambdaExtractor_returnsEmpty() {
        var def = mock(CaseDefinition.class);
        var config = mock(CbrConfig.class);
        when(config.featureExtractor()).thenReturn(mock(LambdaFeatureExtractor.class));
        when(def.getCbrConfig()).thenReturn(config);
        when(registry.findByName("travel-plan")).thenReturn(Optional.of(def));
        assertTrue(extractor.extract("travel-plan", MAPPER.createObjectNode()).isEmpty());
    }

    @Test
    void extract_jqExtractor_extractsFeatures() {
        var def = mock(CaseDefinition.class);
        var config = mock(CbrConfig.class);
        var jq = mock(JqFeatureExtractor.class);
        when(jq.featureExpressions()).thenReturn(Map.of(
                "budget", ".request.budget",
                "destination", ".request.destination"));
        when(config.featureExtractor()).thenReturn(jq);
        when(def.getCbrConfig()).thenReturn(config);
        when(registry.findByName("travel-plan")).thenReturn(Optional.of(def));

        ObjectNode context = MAPPER.createObjectNode();
        ObjectNode request = context.putObject("request");
        request.put("budget", 2000);
        request.put("destination", "Barcelona");

        JsonNode budgetNode = MAPPER.valueToTree(2000);
        JsonNode destNode = MAPPER.valueToTree("Barcelona");
        when(jqEvaluator.eval(eq(".request.budget"), any(JsonNode.class)))
                .thenReturn(new ValidationResult(true, null, List.of(budgetNode)));
        when(jqEvaluator.eval(eq(".request.destination"), any(JsonNode.class)))
                .thenReturn(new ValidationResult(true, null, List.of(destNode)));

        var result = extractor.extract("travel-plan", context);
        assertTrue(result.isPresent());
        assertEquals(config, result.get().config());
        assertEquals(2, result.get().features().size());
    }

    @Test
    void extract_nullJqResult_skipsFeature() {
        var def = mock(CaseDefinition.class);
        var config = mock(CbrConfig.class);
        var jq = mock(JqFeatureExtractor.class);
        when(jq.featureExpressions()).thenReturn(Map.of("budget", ".request.budget"));
        when(config.featureExtractor()).thenReturn(jq);
        when(def.getCbrConfig()).thenReturn(config);
        when(registry.findByName("travel-plan")).thenReturn(Optional.of(def));

        ObjectNode context = MAPPER.createObjectNode();
        JsonNode nullNode = MAPPER.nullNode();
        when(jqEvaluator.eval(eq(".request.budget"), any(JsonNode.class)))
                .thenReturn(new ValidationResult(true, null, List.of(nullNode)));

        var result = extractor.extract("travel-plan", context);
        assertTrue(result.isPresent());
        assertTrue(result.get().features().isEmpty());
    }

    @Test
    void extract_failedJqEval_skipsFeature() {
        var def = mock(CaseDefinition.class);
        var config = mock(CbrConfig.class);
        var jq = mock(JqFeatureExtractor.class);
        when(jq.featureExpressions()).thenReturn(Map.of("budget", ".bad.path"));
        when(config.featureExtractor()).thenReturn(jq);
        when(def.getCbrConfig()).thenReturn(config);
        when(registry.findByName("travel-plan")).thenReturn(Optional.of(def));

        ObjectNode context = MAPPER.createObjectNode();
        when(jqEvaluator.eval(eq(".bad.path"), any(JsonNode.class)))
                .thenReturn(new ValidationResult(false, "error", List.of()));

        var result = extractor.extract("travel-plan", context);
        assertTrue(result.isPresent());
        assertTrue(result.get().features().isEmpty());
    }

    @Test
    void extract_emptyOutputList_skipsFeature() {
        var def = mock(CaseDefinition.class);
        var config = mock(CbrConfig.class);
        var jq = mock(JqFeatureExtractor.class);
        when(jq.featureExpressions()).thenReturn(Map.of("budget", ".request.budget"));
        when(config.featureExtractor()).thenReturn(jq);
        when(def.getCbrConfig()).thenReturn(config);
        when(registry.findByName("travel-plan")).thenReturn(Optional.of(def));

        ObjectNode context = MAPPER.createObjectNode();
        when(jqEvaluator.eval(eq(".request.budget"), any(JsonNode.class)))
                .thenReturn(new ValidationResult(true, null, List.of()));

        var result = extractor.extract("travel-plan", context);
        assertTrue(result.isPresent());
        assertTrue(result.get().features().isEmpty());
    }
}
