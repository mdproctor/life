package io.casehub.life.app.cbr;

import io.casehub.neocortex.memory.cbr.AdaptedStep;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface LifeAdaptationRule {

    String caseType();

    Set<String> knownCapabilities();

    List<AdaptedStep> adapt(ScoredCbrCase<PlanCbrCase> retrieved,
                            Map<String, FeatureValue> currentFeatures);
}
