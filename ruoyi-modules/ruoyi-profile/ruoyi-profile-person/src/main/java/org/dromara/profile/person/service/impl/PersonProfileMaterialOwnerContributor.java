package org.dromara.profile.person.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.profile.api.domain.ProfileType;
import org.dromara.profile.api.material.ProfileMaterialOwnerContributor;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerKey;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerType;
import org.dromara.profile.person.mapper.PersonApplicationMapper;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PersonProfileMaterialOwnerContributor implements ProfileMaterialOwnerContributor {

    private final PersonApplicationMapper mapper;

    @Override
    public ProfileType profileType() {
        return ProfileType.PERSON;
    }

    @Override
    public Optional<ResolvedMaterialOwner> lockOwner(MaterialOwnerKey owner) {
        requirePerson(owner);
        if (owner.ownerType() == MaterialOwnerType.WORKING) {
            return Optional.ofNullable(mapper.lockMaterialWorkingOwner(owner.ownerId()))
                .map(userId -> new ResolvedMaterialOwner(owner, userId));
        }
        if (owner.ownerType() == MaterialOwnerType.SUBMISSION) {
            return Optional.ofNullable(mapper.lockMaterialSubmissionOwner(owner.ownerId()))
                .map(userId -> new ResolvedMaterialOwner(owner, userId));
        }
        return mapper.lockMaterialImmutableOwner(owner.ownerType().name(), owner.ownerId()) != null
            ? Optional.of(new ResolvedMaterialOwner(owner, null))
            : Optional.empty();
    }

    @Override
    public boolean isWorkingEditable(MaterialOwnerKey owner) {
        requirePerson(owner);
        if (owner.ownerType() != MaterialOwnerType.WORKING) {
            throw new IllegalArgumentException("material owner must be WORKING");
        }
        return mapper.countEditableMaterialWorkingOwner(owner.ownerId()) == 1;
    }

    @Override
    public boolean hasSnapshotRelationship(SnapshotRelationship relationship) {
        requirePerson(relationship.source());
        requirePerson(relationship.target());
        return mapper.countMaterialSnapshotRelationship(
            relationship.source().ownerType().name(), relationship.source().ownerId(),
            relationship.target().ownerType().name(), relationship.target().ownerId()) == 1;
    }

    private void requirePerson(MaterialOwnerKey owner) {
        if (owner.profileType() != ProfileType.PERSON) {
            throw new IllegalArgumentException("material owner must use PERSON profileType");
        }
    }
}
