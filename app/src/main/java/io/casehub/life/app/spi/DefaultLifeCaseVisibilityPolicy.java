package io.casehub.life.app.spi;

import io.casehub.life.api.response.LifeCaseResponse;
import io.casehub.life.api.spi.LifeCaseVisibilityPolicy;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;

@ApplicationScoped
public class DefaultLifeCaseVisibilityPolicy implements LifeCaseVisibilityPolicy {
    @Override
    public boolean isVisible(LifeCaseResponse caseResponse, String actorId, Set<String> groups) {
        return true;
    }
}
