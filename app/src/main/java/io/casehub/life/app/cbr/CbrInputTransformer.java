package io.casehub.life.app.cbr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.api.model.WorkerContext;
import io.casehub.api.model.WorkerExecutionContext;
import io.casehub.neocortex.memory.cbr.AdaptedPlan;

import java.util.function.UnaryOperator;

public class CbrInputTransformer implements UnaryOperator<JsonNode> {

    private final LifeCbrExperienceFormatter formatter;
    private final ObjectMapper               objectMapper;

    public CbrInputTransformer(LifeCbrExperienceFormatter formatter, ObjectMapper objectMapper) {
        this.formatter    = formatter;
        this.objectMapper = objectMapper;
    }

    @Override
    public JsonNode apply(JsonNode input) {
        WorkerContext ctx        = WorkerExecutionContext.current();
        ObjectNode    enriched   = input.deepCopy();
        StringBuilder cbrContext = new StringBuilder();

        if (ctx != null && !ctx.experiences().isEmpty()) {
            String experienceText = formatter.format(ctx.experiences());
            if (experienceText != null) {
                cbrContext.append(experienceText);
            }
        }

        JsonNode adaptedPlanNode = input.get("adaptedPlan");
        if (adaptedPlanNode != null) {
            try {
                AdaptedPlan plan     = objectMapper.treeToValue(adaptedPlanNode, AdaptedPlan.class);
                String      planText = formatter.formatAdaptedPlan(plan);
                if (planText != null) {
                    if (!cbrContext.isEmpty()) {cbrContext.append("\n\n");}
                    cbrContext.append(planText);
                }
            } catch (Exception e) {
                // malformed adaptedPlan — skip silently
            }
            enriched.remove("adaptedPlan");
        }

        if (cbrContext.isEmpty()) {
            return input;
        }
        enriched.put("_cbrContext", cbrContext.toString());
        return enriched;
    }
}
