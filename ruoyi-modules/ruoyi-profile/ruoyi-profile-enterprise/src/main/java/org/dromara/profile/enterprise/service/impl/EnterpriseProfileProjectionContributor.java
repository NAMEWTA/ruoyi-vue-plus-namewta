package org.dromara.profile.enterprise.service.impl;

import org.dromara.profile.api.ProfileProjectionContributor;
import org.dromara.profile.api.domain.ProfileBindingSummary;
import org.dromara.profile.api.domain.ProfileType;
import org.dromara.profile.enterprise.domain.vo.EnterpriseActiveProjectionRow;
import org.dromara.profile.enterprise.mapper.EnterpriseApplicationMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class EnterpriseProfileProjectionContributor implements ProfileProjectionContributor {

    private final EnterpriseApplicationMapper mapper;

    public EnterpriseProfileProjectionContributor(EnterpriseApplicationMapper mapper) {
        this.mapper = mapper;
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
        for (EnterpriseActiveProjectionRow projection : mapper.selectActiveProjections(userIds)) {
            ProfileBindingSummary previous = result.put(projection.getUserId(), new ProfileBindingSummary(
                projection.getEnterpriseProfileId(), ProfileType.ENTERPRISE, projection.getVerifiedAt()));
            if (previous != null) {
                throw new IllegalStateException("Duplicate active enterprise projection");
            }
        }
        return Map.copyOf(result);
    }
}
