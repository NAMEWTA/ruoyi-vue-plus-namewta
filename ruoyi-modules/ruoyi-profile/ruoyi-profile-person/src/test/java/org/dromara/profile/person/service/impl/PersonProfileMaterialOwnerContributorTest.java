package org.dromara.profile.person.service.impl;

import org.dromara.profile.api.domain.ProfileType;
import org.dromara.profile.api.material.ProfileMaterialOwnerContributor.ResolvedMaterialOwner;
import org.dromara.profile.api.material.ProfileMaterialOwnerContributor.SnapshotRelationship;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerKey;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerType;
import org.dromara.profile.person.mapper.PersonApplicationMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class PersonProfileMaterialOwnerContributorTest {

    private final PersonApplicationMapper mapper = mock(PersonApplicationMapper.class);
    private final PersonProfileMaterialOwnerContributor contributor =
        new PersonProfileMaterialOwnerContributor(mapper);

    @Test
    void resolvesEditableAndImmutablePersonOwnersThroughPersonMapper() {
        MaterialOwnerKey working = owner(MaterialOwnerType.WORKING, 91L);
        MaterialOwnerKey source = owner(MaterialOwnerType.SOURCE, 92L);
        when(mapper.lockMaterialWorkingOwner(91L)).thenReturn(41L);
        when(mapper.countEditableMaterialWorkingOwner(91L)).thenReturn(1L);
        when(mapper.lockMaterialImmutableOwner("SOURCE", 92L)).thenReturn(92L);

        assertThat(contributor.lockOwner(working))
            .contains(new ResolvedMaterialOwner(working, 41L));
        assertThat(contributor.isWorkingEditable(working)).isTrue();
        assertThat(contributor.lockOwner(source))
            .contains(new ResolvedMaterialOwner(source, null));
    }

    @Test
    void checksSnapshotRelationshipsAndRejectsForeignProfileTypes() {
        MaterialOwnerKey submission = owner(MaterialOwnerType.SUBMISSION, 93L);
        MaterialOwnerKey version = owner(MaterialOwnerType.VERSION, 94L);
        SnapshotRelationship relationship = new SnapshotRelationship(submission, version);
        when(mapper.countMaterialSnapshotRelationship("SUBMISSION", 93L, "VERSION", 94L))
            .thenReturn(1L);

        assertThat(contributor.hasSnapshotRelationship(relationship)).isTrue();
        verify(mapper).countMaterialSnapshotRelationship("SUBMISSION", 93L, "VERSION", 94L);

        MaterialOwnerKey enterprise = new MaterialOwnerKey(
            ProfileType.ENTERPRISE, MaterialOwnerType.WORKING, 95L);
        assertThatThrownBy(() -> contributor.lockOwner(enterprise))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("PERSON");
    }

    private MaterialOwnerKey owner(MaterialOwnerType type, long id) {
        return new MaterialOwnerKey(ProfileType.PERSON, type, id);
    }
}
