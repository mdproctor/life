package io.casehub.life.app.spi;

import io.casehub.life.api.HouseholdGroups;
import io.casehub.life.api.response.LifeCaseResponse;
import io.casehub.life.api.spi.LifeCaseVisibilityPolicy;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.Set;

@Alternative
@Priority(1)
@ApplicationScoped
public class JuniorLifeCaseVisibilityPolicy implements LifeCaseVisibilityPolicy {
    @Override
    public boolean isVisible(LifeCaseResponse caseResponse, String actorId, Set<String> groups) {
        if (groups.contains(HouseholdGroups.ADMIN) || groups.contains(HouseholdGroups.MEMBER)) {
            return true;
        }
        return false;
    }
}
