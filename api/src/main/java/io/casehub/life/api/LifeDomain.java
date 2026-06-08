package io.casehub.life.api;

import io.casehub.life.api.descriptor.*;

import java.util.Arrays;
import java.util.Optional;

public enum LifeDomain {
    HEALTH(new HealthDomainDescriptor()),
    FINANCE(new FinanceDomainDescriptor()),
    FAMILY_SCHEDULING(new FamilySchedulingDomainDescriptor()),
    TRAVEL(new TravelDomainDescriptor()),
    LEGAL(new LegalDomainDescriptor()),
    CONTRACTOR_COORDINATION(new ContractorCoordinationDomainDescriptor()),
    ELDER_CARE(new ElderCareDomainDescriptor()),
    HOUSEHOLD(new HouseholdDomainDescriptor());

    private final LifeDomainDescriptor descriptor;

    LifeDomain(LifeDomainDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    public LifeDomainDescriptor descriptor() {
        return descriptor;
    }

    public static Optional<LifeDomain> fromCategory(String category) {
        if (category == null) return Optional.empty();
        return Arrays.stream(values())
                .filter(d -> d.descriptor().templateCategory().equals(category))
                .findFirst();
    }
}
