package org.dromara.profile.person.service.impl;

import org.dromara.profile.api.domain.ProfileType;
import org.dromara.profile.api.material.ProfileMaterialOwnerContributor;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerKey;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerType;
import org.dromara.profile.person.dao.PersonApplicationDao;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 创建个人材料所有者处理器。
 */
@Component
public class PersonProfileMaterialOwnerContributor implements ProfileMaterialOwnerContributor {

    private final PersonApplicationDao dao;

    /**
     * 处理personprofilematerialownercontributor。
     */
    public PersonProfileMaterialOwnerContributor(PersonApplicationDao dao) {
        this.dao = dao;
    }

    /**
     * 返回材料所属档案类型
     */
    @Override
    public ProfileType profileType() {
        return ProfileType.PERSON;
    }

    /**
     * 锁定材料所有者记录
     */
    @Override
    public Optional<ResolvedMaterialOwner> lockOwner(MaterialOwnerKey owner) {
        requirePerson(owner);
        if (owner.ownerType() == MaterialOwnerType.WORKING) {
            return Optional.ofNullable(dao.lockMaterialWorkingOwner(owner.ownerId()))
                .map(userId -> new ResolvedMaterialOwner(owner, userId));
        }
        if (owner.ownerType() == MaterialOwnerType.SUBMISSION) {
            return Optional.ofNullable(dao.lockMaterialSubmissionOwner(owner.ownerId()))
                .map(userId -> new ResolvedMaterialOwner(owner, userId));
        }
        return dao.lockMaterialImmutableOwner(owner.ownerType().name(), owner.ownerId()) != null
            ? Optional.of(new ResolvedMaterialOwner(owner, null))
            : Optional.empty();
    }

    /**
     * 判断档案是否处于可编辑状态
     */
    @Override
    public boolean isWorkingEditable(MaterialOwnerKey owner) {
        requirePerson(owner);
        if (owner.ownerType() != MaterialOwnerType.WORKING) {
            throw new IllegalArgumentException("material owner must be WORKING");
        }
        return dao.countEditableMaterialWorkingOwner(owner.ownerId()) == 1;
    }

    /**
     * 判断快照是否具有关联关系
     */
    @Override
    public boolean hasSnapshotRelationship(SnapshotRelationship relationship) {
        requirePerson(relationship.source());
        requirePerson(relationship.target());
        return dao.countMaterialSnapshotRelationship(
            relationship.source().ownerType().name(), relationship.source().ownerId(),
            relationship.target().ownerType().name(), relationship.target().ownerId()) == 1;
    }

    /**
     * 校验并获取个人档案
     */
    private void requirePerson(MaterialOwnerKey owner) {
        if (owner.profileType() != ProfileType.PERSON) {
            throw new IllegalArgumentException("material owner must use PERSON profileType");
        }
    }
}
