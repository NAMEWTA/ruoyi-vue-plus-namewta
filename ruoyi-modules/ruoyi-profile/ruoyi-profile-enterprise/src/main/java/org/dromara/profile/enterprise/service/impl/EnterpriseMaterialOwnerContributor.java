package org.dromara.profile.enterprise.service.impl;

import org.dromara.profile.api.domain.ProfileType;
import org.dromara.profile.api.material.ProfileMaterialOwnerContributor;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerKey;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerType;
import org.dromara.profile.enterprise.dao.EnterpriseApplicationDao;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 企业档案材料 owner 的模块内权威解析器。
 */
@Component
public class EnterpriseMaterialOwnerContributor implements ProfileMaterialOwnerContributor {

    private final EnterpriseApplicationDao dao;

    /**
     * 处理enterprisematerialownercontributor。
     */
    public EnterpriseMaterialOwnerContributor(EnterpriseApplicationDao dao) {
        this.dao = dao;
    }

    /**
     * 返回材料所属档案类型
     */
    @Override
    public ProfileType profileType() {
        return ProfileType.ENTERPRISE;
    }

    /**
     * 锁定材料所有者记录
     */
    @Override
    public Optional<ResolvedMaterialOwner> lockOwner(MaterialOwnerKey owner) {
        requireOwned(owner);
        Long value = switch (owner.ownerType()) {
            case WORKING -> dao.lockMaterialWorkingOwner(owner.ownerId());
            case SUBMISSION -> dao.lockMaterialSubmissionOwner(owner.ownerId());
            case SOURCE -> dao.lockMaterialSourceOwner(owner.ownerId());
            case VERSION -> dao.lockMaterialVersionOwner(owner.ownerId());
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

    /**
     * 判断档案是否处于可编辑状态
     */
    @Override
    public boolean isWorkingEditable(MaterialOwnerKey owner) {
        requireOwned(owner);
        if (owner.ownerType() != MaterialOwnerType.WORKING) {
            throw new IllegalArgumentException("editable owner must use WORKING");
        }
        return dao.countEditableMaterialWorkingOwner(owner.ownerId()) > 0;
    }

    /**
     * 判断快照是否具有关联关系
     */
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
            return dao.countWorkingSubmissionRelationship(source.ownerId(), target.ownerId()) > 0;
        }
        if (source.ownerType() == MaterialOwnerType.SUBMISSION
            && target.ownerType() == MaterialOwnerType.VERSION) {
            return dao.countSubmissionVersionRelationship(source.ownerId(), target.ownerId()) > 0;
        }
        if (source.ownerType() == MaterialOwnerType.SOURCE
            && target.ownerType() == MaterialOwnerType.VERSION) {
            return dao.countSourceVersionRelationship(source.ownerId(), target.ownerId()) > 0;
        }
        return false;
    }

    /**
     * 校验材料属于当前所有者
     */
    private void requireOwned(MaterialOwnerKey owner) {
        if (owner == null || owner.profileType() != ProfileType.ENTERPRISE) {
            throw new IllegalArgumentException("material owner must use ENTERPRISE profileType");
        }
    }
}
