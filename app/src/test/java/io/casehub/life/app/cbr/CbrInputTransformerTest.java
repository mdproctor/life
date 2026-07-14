package io.casehub.life.app.cbr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.api.model.WorkerContext;
import io.casehub.api.model.WorkerExecutionContext;
import io.casehub.api.spi.routing.RetrievedExperience;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CbrInputTransformerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @AfterEach
    void cleanup() {
        WorkerExecutionContext.clear();
    }

    @Test
    void apply_noWorkerContext_passThrough() {
        var transformer = new CbrInputTransformer(new LifeCbrExperienceFormatter());
        ObjectNode input = MAPPER.createObjectNode().put("key", "value");
        JsonNode result = transformer.apply(input);
        assertEquals("value", result.get("key").asText());
        assertFalse(result.has("_cbrContext"));
    }

    @Test
    void apply_emptyExperiences_passThrough() {
        var transformer = new CbrInputTransformer(new LifeCbrExperienceFormatter());
        WorkerExecutionContext.set(new WorkerContext("task", null, List.of(), List.of(), null, Map.of(), List.of()));
        ObjectNode input = MAPPER.createObjectNode().put("key", "value");
        JsonNode result = transformer.apply(input);
        assertFalse(result.has("_cbrContext"));
    }

    @Test
    void apply_withExperiences_mergesCbrContext() {
        var transformer = new CbrInputTransformer(new LifeCbrExperienceFormatter());
        var exp = new RetrievedExperience("problem", "solution", "COMPLETED", 0.9,
                0.85, Map.of(), List.of(), Map.of());
        WorkerExecutionContext.set(new WorkerContext("task", null, List.of(), List.of(), null, Map.of(), List.of(exp)));
        ObjectNode input = MAPPER.createObjectNode().put("key", "value");
        JsonNode result = transformer.apply(input);
        assertTrue(result.has("_cbrContext"));
        assertTrue(result.get("_cbrContext").asText().contains("problem"));
        assertEquals("value", result.get("key").asText());
    }

    @Test
    void apply_doesNotMutateOriginalInput() {
        var transformer = new CbrInputTransformer(new LifeCbrExperienceFormatter());
        var exp = new RetrievedExperience("problem", "solution", "COMPLETED", 0.9,
                0.85, Map.of(), List.of(), Map.of());
        WorkerExecutionContext.set(new WorkerContext("task", null, List.of(), List.of(), null, Map.of(), List.of(exp)));
        ObjectNode input = MAPPER.createObjectNode().put("key", "value");
        transformer.apply(input);
        assertFalse(input.has("_cbrContext"));
    }
}
