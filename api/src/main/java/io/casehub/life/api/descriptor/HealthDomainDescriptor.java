package io.casehub.life.api.descriptor;

import io.casehub.life.api.LifeCapabilities;
import io.casehub.life.api.LifeDomainDescriptor;
import io.casehub.life.api.LifeRoutingPolicy;
import io.casehub.life.api.LifeSlaPolicy;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

public final class HealthDomainDescriptor implements LifeDomainDescriptor {
    @Override public String capability()       { return LifeCapabilities.HEALTH_COORDINATION; }
    @Override public String templateCategory() { return "health"; }

    @Override public Set<String> workerCapabilities() {
        return Set.of("book-appointment", "find-alternative", "confirm-appointment",
                      "pre-visit-prep", "record-health-decision");
    }

    @Override public LifeRoutingPolicy routingPolicy() {
        return new LifeRoutingPolicy(OptionalDouble.of(0.75), OptionalInt.of(10),
                OptionalDouble.of(0.05), Optional.of("household-admin"),
                "High reliability required for health appointments and follow-ups");
    }

    @Override public LifeSlaPolicy slaPolicy() {
        return new LifeSlaPolicy("household-admin", Duration.ofHours(24));
    }
}
