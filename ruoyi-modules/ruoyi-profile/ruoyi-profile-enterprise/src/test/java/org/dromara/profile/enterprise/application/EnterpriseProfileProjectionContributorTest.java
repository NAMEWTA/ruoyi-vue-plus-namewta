package org.dromara.profile.enterprise.application;

import org.dromara.profile.api.domain.ProfileType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("dev")
class EnterpriseProfileProjectionContributorTest {

    @Test
    void returnsOnlyRepositoryApprovedActiveProjection() {
        EnterpriseApplicationRepository repository = mock(EnterpriseApplicationRepository.class);
        when(repository.findActiveProjections(Set.of(101L, 102L))).thenReturn(List.of(
            new EnterpriseActiveProjection(101L, 9001L, Instant.parse("2026-09-01T12:00:00Z"))));

        EnterpriseProfileProjectionContributor contributor = new EnterpriseProfileProjectionContributor(repository);

        assertThat(contributor.profileType()).isEqualTo(ProfileType.ENTERPRISE);
        assertThat(contributor.findActiveBindings(Set.of(101L, 102L))).containsOnlyKeys(101L);
        assertThat(contributor.findActiveBindings(Set.of(101L, 102L)).get(101L).profileId()).isEqualTo(9001L);
    }
}
