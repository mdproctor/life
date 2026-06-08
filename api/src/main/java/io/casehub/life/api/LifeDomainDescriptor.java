package io.casehub.life.api;

import java.util.Set;

public interface LifeDomainDescriptor {
    String capability();
    String templateCategory();
    LifeRoutingPolicy routingPolicy();
    Set<String> workerCapabilities();
    LifeSlaPolicy slaPolicy();
}
