package io.casehub.life.app.cbr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.api.spi.CaseOutcomeEvent;
import io.casehub.api.spi.CaseOutcomeObserver;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.platform.api.path.Path;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class LifeCaseOutcomeCbrWriter implements CaseOutcomeObserver {

    static final String TENANT_ID = "life-personal";

    private static final Logger LOG = Logger.getLogger(LifeCaseOutcomeCbrWriter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CbrCaseMemoryStore cbrStore;
    private final LifeCbrFeatureExtractor featureExtractor;
    private final Map<String, LifeCbrDescriptionProvider> providers;

    @Inject
    public LifeCaseOutcomeCbrWriter(CbrCaseMemoryStore cbrStore,
                                    LifeCbrFeatureExtractor featureExtractor,
                                    Instance<LifeCbrDescriptionProvider> providers) {
        this.cbrStore = cbrStore;
        this.featureExtractor = featureExtractor;
        this.providers = new HashMap<>();
        providers.stream().forEach(p -> this.providers.put(p.caseType(), p));
    }

    @Override
    public void onOutcome(CaseOutcomeEvent event) {
        LifeCbrDescriptionProvider descProvider = providers.get(event.caseType());
        if (descProvider == null) {return;}

        try {
            JsonNode jsonNode   = MAPPER.valueToTree(event.caseFileSnapshot());
            var      extraction = featureExtractor.extract(event.caseType(), jsonNode);
            if (extraction.isEmpty()) {return;}

            var result = extraction.get();

            PlanCbrCase cbrCase = new PlanCbrCase(
                    descProvider.describeProblem(event.caseFileSnapshot()),
                    descProvider.describeSolution(event.caseFileSnapshot()),
                    event.outcomeLabel(),
                    null,
                    result.features(),
                    List.of());

            cbrStore.store(
                    cbrCase,
                    event.caseType(),
                    descProvider.extractEntityId(event.caseFileSnapshot(), event.caseId()),
                    new MemoryDomain(result.config().domain()),
                    TENANT_ID,
                    event.caseId().toString(),
                    Path.parse(result.config().domain()));

        } catch (Exception e) {
            LOG.warnf(e, "CBR retention failed for case %s (%s) — proceeding without recording",
                      event.caseId(), event.caseType());
        }}

}
