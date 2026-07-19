package io.casehub.life.api.spi;

import io.casehub.life.api.response.LifeCaseResponse;

import java.util.Set;

public interface LifeCaseVisibilityPolicy {
    boolean isVisible(LifeCaseResponse caseResponse, String actorId, Set<String> groups);
}
