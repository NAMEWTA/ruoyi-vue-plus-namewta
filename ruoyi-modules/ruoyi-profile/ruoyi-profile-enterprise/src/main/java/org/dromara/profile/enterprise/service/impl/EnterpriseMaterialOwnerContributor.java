package org.dromara.profile.enterprise.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.profile.api.domain.ProfileType;
import org.dromara.profile.api.material.ProfileMaterialOwnerContributor;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerKey;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerType;
import org.dromara.profile.enterprise.mapper.EnterpriseApplicationMapper;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 企业档案材料 owner 的模块内权威解析器。
 */
@Component
@RequiredArgsConstructor
public class EnterpriseMaterialOwnerContributor implements ProfileMaterialOwnerContributor {

    private final EnterpriseApplicationMapper mapper;

    @Override
    public ProfileType profileType() {
        return ProfileType.ENTERPRISE;
    }

    @Override
    public Optional<ResolvedMaterialOwner> lockOwner(MaterialOwnerKey owner) {
        requireOwned(owner);
        Long value = switch (owner.ownerType()) {
            case WORKING -> mapper.lockMaterialWorkingOwner(owner.ownerId());
            case SUBMISSION -> mapper.lockMaterialSubmissionOwner(owner.ownerId());
            case SOURCE -> mapper.lockMaterialSourceOwner(owner.ownerId());
            case VERSION -> mapper.lockMaterialVersionOwner(owner.ownerId());
        };
        if (value == null) {
            return Optional.empty();
        }
        Long applicantUserId = switch (owner.ownerType()) {
            case WORKING, SUBMISSION -> value;
            case SOURCE, VERSION -> null;
        };
        return Optional.of(new ResolvedMaterialOwner(owner, applicantUserId));
    }

    @Override
    public boolean isWorkingEditable(MaterialOwnerKey owner) {
        requireOwned(owner);
        if (owner.ownerType() != MaterialOwnerType.WORKING) {
            throw new IllegalArgumentException("editable owner must use WORKING");
        }
        return mapper.countEditableMaterialWorkingOwner(owner.ownerId()) > 0;
    }

    @Override
    public boolean hasSnapshotRelationship(SnapshotRelationship relationship) {
        if (relationship == null) {
            throw new IllegalArgumentException("relationship must not be null");
        }
        requireOwned(relationship.source());
        requireOwned(relationship.target());
        MaterialOwnerKey source = relationship.source();
        MaterialOwnerKey target = relationship.target();
        if (source.ownerType() == MaterialOwnerType.WORKING
            && target.ownerType() == MaterialOwnerType.SUBMISSION) {
            return mapper.countWorkingSubmissionRelationship(source.ownerId(), target.ownerId()) > 0;
        }
        if (source.ownerType() == MaterialOwnerType.SUBMISSION
            && target.ownerType() == MaterialOwnerType.VERSION) {
            return mapper.countSubmissionVersionRelationship(source.ownerId(), target.ownerId()) > 0;
        }
        if (source.ownerType() == MaterialOwnerType.SOURCE
            && target.ownerType() == MaterialOwnerType.VERSION) {
            return mapper.countSourceVersionRelationship(source.ownerId(), target.ownerId()) > 0;
        }
        return false;
    }

    private void requireOwned(MaterialOwnerKey owner) {
        if (owner == null || owner.profileType() != ProfileType.ENTERPRISE) {
            throw new IllegalArgumentException("material owner must use ENTERPRISE profileType");
        }
    }
}
