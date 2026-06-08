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

public final class ElderCareDomainDescriptor implements LifeDomainDescriptor {
    @Override public String capability()       { return LifeCapabilities.ELDER_CARE; }
    @Override public String templateCategory() { return "elder-care"; }

    @Override public Set<String> workerCapabilities() {
        return Set.of("needs-assessment", "care-plan", "health-check",
                      "assess-patient", "provide-care");
    }

    @Override public LifeRoutingPolicy routingPolicy() {
        return new LifeRoutingPolicy(OptionalDouble.of(0.75), OptionalInt.of(10),
                OptionalDouble.of(0.05), Optional.of("household-admin"),
                "Care coordination requires high reliability and proactive alerting");
    }

    @Override public LifeSlaPolicy slaPolicy() {
        return new LifeSlaPolicy("household-admin", Duration.ofHours(12));
    }
}
