package org.dromara.profile.person.application;

import org.dromara.profile.api.ProfileProjectionContributor;
import org.dromara.profile.api.domain.ProfileBindingSummary;
import org.dromara.profile.api.domain.ProfileType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class PersonProfileProjectionContributor implements ProfileProjectionContributor {

    private final PersonApplicationRepository repository;

    public PersonProfileProjectionContributor(PersonApplicationRepository repository) {
        this.repository = repository;
    }

    @Override
    public ProfileType profileType() {
        return ProfileType.PERSON;
    }

    @Override
    public Map<Long, ProfileBindingSummary> findActiveBindings(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ProfileBindingSummary> result = new LinkedHashMap<>();
        for (PersonActiveProjection projection : repository.findActiveProjections(userIds)) {
            ProfileBindingSummary previous = result.put(projection.userId(), new ProfileBindingSummary(
                projection.personProfileId(), ProfileType.PERSON, projection.verifiedAt()));
            if (previous != null) {
                throw new IllegalStateException("Duplicate active person projection");
            }
        }
        return Map.copyOf(result);
    }
}
