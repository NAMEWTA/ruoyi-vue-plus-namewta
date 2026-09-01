package org.dromara.profile.enterprise.application;

import org.dromara.profile.api.ProfileProjectionContributor;
import org.dromara.profile.api.domain.ProfileBindingSummary;
import org.dromara.profile.api.domain.ProfileType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class EnterpriseProfileProjectionContributor implements ProfileProjectionContributor {

    private final EnterpriseApplicationRepository repository;

    public EnterpriseProfileProjectionContributor(EnterpriseApplicationRepository repository) {
        this.repository = repository;
    }

    @Override
    public ProfileType profileType() {
        return ProfileType.ENTERPRISE;
    }

    @Override
    public Map<Long, ProfileBindingSummary> findActiveBindings(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ProfileBindingSummary> result = new LinkedHashMap<>();
        for (EnterpriseActiveProjection projection : repository.findActiveProjections(userIds)) {
            ProfileBindingSummary previous = result.put(projection.userId(), new ProfileBindingSummary(
                projection.enterpriseProfileId(), ProfileType.ENTERPRISE, projection.verifiedAt()));
            if (previous != null) {
                throw new IllegalStateException("Duplicate active enterprise projection");
            }
        }
        return Map.copyOf(result);
    }
}
