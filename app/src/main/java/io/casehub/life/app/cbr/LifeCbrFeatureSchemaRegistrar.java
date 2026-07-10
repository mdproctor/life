package io.casehub.life.app.cbr;

import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrFeatureSchema;
import io.casehub.neocortex.memory.cbr.FeatureField;
import io.casehub.neocortex.memory.cbr.SimilaritySpec;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class LifeCbrFeatureSchemaRegistrar {

    private final CbrCaseMemoryStore cbrStore;

    @Inject
    public LifeCbrFeatureSchemaRegistrar(CbrCaseMemoryStore cbrStore) {
        this.cbrStore = cbrStore;
    }

    void onStartup(@Observes StartupEvent event) {
        cbrStore.registerSchema(contractorCoordination());
        cbrStore.registerSchema(homeMaintenance());
        cbrStore.registerSchema(appointmentCycle());
        cbrStore.registerSchema(careCoordination());
        cbrStore.registerSchema(financialReview());
        cbrStore.registerSchema(travelPlan());
    }

    private static final SimilaritySpec SEASON_TABLE = SimilaritySpec.categoricalTableBuilder()
            .add("spring", "summer", 0.7)
            .add("spring", "autumn", 0.5)
            .add("spring", "winter", 0.3)
            .add("summer", "autumn", 0.5)
            .add("summer", "winter", 0.3)
            .add("autumn", "winter", 0.7)
            .build();

    private static final SimilaritySpec SEVERITY_TABLE = SimilaritySpec.categoricalTableBuilder()
            .add("low", "medium", 0.6)
            .add("low", "high", 0.3)
            .add("low", "critical", 0.1)
            .add("medium", "high", 0.6)
            .add("medium", "critical", 0.3)
            .add("high", "critical", 0.8)
            .build();

    private static final SimilaritySpec COST_DECAY = new SimilaritySpec.GaussianDecay(0.3);
    private static final SimilaritySpec TIME_DECAY = new SimilaritySpec.GaussianDecay(0.25);

    private static CbrFeatureSchema contractorCoordination() {
        return CbrFeatureSchema.of("contractor-coordination",
                FeatureField.categorical("problemType"),
                FeatureField.categorical("season", SEASON_TABLE),
                FeatureField.categorical("propertyArea"),
                FeatureField.numeric("budget", 0, 10_000, COST_DECAY),
                FeatureField.numeric("quotedCost", 0, 10_000, COST_DECAY),
                FeatureField.numeric("slaHours", 1, 720, TIME_DECAY));
    }

    private static CbrFeatureSchema homeMaintenance() {
        return CbrFeatureSchema.of("home-maintenance",
                FeatureField.categorical("issueType"),
                FeatureField.categorical("severity", SEVERITY_TABLE),
                FeatureField.categorical("season", SEASON_TABLE),
                FeatureField.numeric("estimatedCost", 0, 10_000, COST_DECAY),
                FeatureField.numeric("resolutionDays", 1, 90, TIME_DECAY));
    }

    private static CbrFeatureSchema appointmentCycle() {
        return CbrFeatureSchema.of("appointment-cycle",
                FeatureField.categorical("conditionCategory"),
                FeatureField.categorical("providerType"),
                FeatureField.numeric("followUpIntervalDays", 1, 365, TIME_DECAY));
    }

    private static CbrFeatureSchema careCoordination() {
        return CbrFeatureSchema.of("care-coordination",
                FeatureField.categorical("careType"),
                FeatureField.categorical("patientRiskLevel", SEVERITY_TABLE),
                FeatureField.numeric("hoursPerWeek", 1, 168, TIME_DECAY));
    }

    private static CbrFeatureSchema financialReview() {
        return CbrFeatureSchema.of("financial-review",
                FeatureField.categorical("category"),
                FeatureField.categorical("amountRange"),
                FeatureField.numeric("amount", 0, 10_000, COST_DECAY),
                FeatureField.numeric("approvalThreshold", 0, 10_000, COST_DECAY));
    }

    private static CbrFeatureSchema travelPlan() {
        return CbrFeatureSchema.of("travel-plan",
                FeatureField.categorical("destination"),
                FeatureField.categorical("travelType"),
                FeatureField.categorical("season", SEASON_TABLE),
                FeatureField.numeric("budget", 0, 10_000, COST_DECAY),
                FeatureField.numeric("durationDays", 1, 90, TIME_DECAY),
                FeatureField.numeric("partySize", 1, 20));
    }
}
