package io.casehub.life.app.cbr;

import io.casehub.api.spi.routing.ExperiencePlanStep;
import io.casehub.api.spi.routing.RetrievedExperience;
import jakarta.enterprise.context.ApplicationScoped;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class LifeCbrExperienceFormatter {

    private static final int MAX_EXPERIENCES = 5;

    public @Nullable String format(List<RetrievedExperience> experiences) {
        if (experiences == null || experiences.isEmpty()) return null;

        List<RetrievedExperience> sorted = experiences.stream()
                .sorted(Comparator.comparingDouble(RetrievedExperience::similarityScore).reversed())
                .limit(MAX_EXPERIENCES)
                .toList();

        StringBuilder sb = new StringBuilder();
        for (var exp : sorted) {
            sb.append("## Similar Case (similarity: ")
              .append(String.format("%.2f", exp.similarityScore())).append(")\n");
            sb.append("Problem: ").append(exp.problem()).append('\n');
            sb.append("Solution: ").append(exp.solution()).append('\n');
            sb.append("Outcome: ").append(exp.outcome()).append('\n');

            if (!exp.features().isEmpty()) {
                String featureStr = exp.features().entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .collect(Collectors.joining(", "));
                sb.append("Key features: ").append(featureStr).append('\n');
            }

            if (!exp.featureSimilarities().isEmpty()) {
                String simStr = exp.featureSimilarities().entrySet().stream()
                        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                        .map(e -> e.getKey() + " (" + String.format("%.2f", e.getValue()) + ")")
                        .collect(Collectors.joining(", "));
                sb.append("Most similar on: ").append(simStr).append('\n');
            }

            if (!exp.planTrace().isEmpty()) {
                sb.append("Plan trace:\n");
                for (ExperiencePlanStep step : exp.planTrace()) {
                    sb.append("  - ").append(step.capabilityName())
                      .append(": ").append(step.stepOutcome()).append('\n');
                }
            }

            sb.append('\n');
        }
        return sb.toString().stripTrailing();
    }
}
