package io.casehub.life.app.cbr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.api.model.WorkerContext;
import io.casehub.api.model.WorkerExecutionContext;

import java.util.function.UnaryOperator;

public class CbrInputTransformer implements UnaryOperator<JsonNode> {

    private final LifeCbrExperienceFormatter formatter;

    public CbrInputTransformer(LifeCbrExperienceFormatter formatter) {
        this.formatter = formatter;
    }

    @Override
    public JsonNode apply(JsonNode input) {
        WorkerContext ctx = WorkerExecutionContext.current();
        if (ctx == null || ctx.experiences().isEmpty()) {
            return input;
        }
        String formatted = formatter.format(ctx.experiences());
        if (formatted == null) {
            return input;
        }
        ObjectNode enriched = input.deepCopy();
        enriched.put("_cbrContext", formatted);
        return enriched;
    }
}
