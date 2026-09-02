package org.dromara.profile.person.service.impl;

import org.dromara.profile.person.service.ProfileMaterialAccessPolicy;

import org.dromara.profile.person.domain.exception.ProfileMaterialException;
import org.dromara.profile.person.domain.material.MaterialOwner;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerType;
import cn.dev33.satoken.stp.StpUtil;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class SaTokenProfileMaterialAccessPolicy implements ProfileMaterialAccessPolicy {

    @Override
    public void requireCatalogManage() {
        require(StpUtil.hasPermission("profile:material-tag:manage"));
    }

    @Override
    public void requireCatalogRead() {
        require(StpUtil.hasPermission("profile:material-tag:query")
            || StpUtil.hasPermission("profile:material-tag:manage"));
    }

    @Override
    public Long requireAttach(MaterialOwner owner) {
        String prefix = prefix(owner);
        Long currentUserId = LoginHelper.getUserId();
        boolean applicant = owner.key().ownerType() == MaterialOwnerType.WORKING
            && StpUtil.hasPermission(prefix + ":material")
            && owner.applicantUserId() != null
            && Objects.equals(owner.applicantUserId(), currentUserId);
        boolean administrator = owner.key().ownerType() == MaterialOwnerType.SOURCE
            && StpUtil.hasPermission(prefix + ":override");
        require(applicant || administrator);
        return currentUserId;
    }

    @Override
    public void requireWrite(MaterialOwner owner) {
        String prefix = prefix(owner);
        require(StpUtil.hasPermission(prefix + ":material")
            && owner.applicantUserId() != null
            && Objects.equals(owner.applicantUserId(), LoginHelper.getUserId()));
    }

    @Override
    public void requireRead(MaterialOwner owner) {
        String prefix = prefix(owner);
        boolean management = StpUtil.hasPermission(prefix + ":query")
            || StpUtil.hasPermission(prefix + ":review")
            || StpUtil.hasPermission(prefix + ":manage")
            || StpUtil.hasPermission(prefix + ":override");
        boolean applicant = StpUtil.hasPermission(prefix + ":material")
            && owner.applicantUserId() != null
            && Objects.equals(owner.applicantUserId(), LoginHelper.getUserId());
        require(management || applicant);
    }

    private String prefix(MaterialOwner owner) {
        return "profile:" + owner.key().profileType().name().toLowerCase(java.util.Locale.ROOT);
    }

    private void require(boolean allowed) {
        if (!allowed) {
            throw new ProfileMaterialException("MATERIAL_ACCESS_DENIED");
        }
    }
}
