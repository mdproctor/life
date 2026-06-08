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

public final class TravelDomainDescriptor implements LifeDomainDescriptor {
    @Override public String capability()       { return LifeCapabilities.TRAVEL_PLANNING; }
    @Override public String templateCategory() { return "travel"; }

    @Override public Set<String> workerCapabilities() {
        return Set.of("destination-research", "flight-search", "hotel-search",
                      "budget-assessment", "booking", "rebooking", "confirmation");
    }

    @Override public LifeRoutingPolicy routingPolicy() {
        return new LifeRoutingPolicy(OptionalDouble.of(0.55), OptionalInt.of(6),
                OptionalDouble.of(0.05), Optional.of("household-admin"),
                "Travel research and booking require moderate reliability");
    }

    @Override public LifeSlaPolicy slaPolicy() {
        return new LifeSlaPolicy("household-admin", Duration.ofHours(48));
    }
}
