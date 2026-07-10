package io.casehub.life.app.cbr;

import java.util.Map;
import java.util.UUID;

public interface LifeCbrDescriptionProvider {
    String caseType();
    String describeProblem(Map<String, Object> caseData);
    String describeSolution(Map<String, Object> caseData);
    String extractEntityId(Map<String, Object> caseData, UUID caseId);
}
