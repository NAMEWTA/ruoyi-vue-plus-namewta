package org.dromara.profile.enterprise.service.impl;

import org.dromara.profile.api.ProfileProjectionContributor;
import org.dromara.profile.api.domain.ProfileBindingSummary;
import org.dromara.profile.api.domain.ProfileType;
import org.dromara.profile.enterprise.domain.model.read.EnterpriseActiveProjectionRow;
import org.dromara.profile.enterprise.dao.EnterpriseApplicationDao;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 创建企业档案投影处理器。
 */
@Component
public class EnterpriseProfileProjectionContributor implements ProfileProjectionContributor {

    private final EnterpriseApplicationDao dao;

    /**
     * 处理enterpriseprofileprojectioncontributor。
     */
    public EnterpriseProfileProjectionContributor(EnterpriseApplicationDao dao) {
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
     * 查询生效的绑定关系
     */
    @Override
    public Map<Long, ProfileBindingSummary> findActiveBindings(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ProfileBindingSummary> result = new LinkedHashMap<>();
        for (EnterpriseActiveProjectionRow projection : dao.selectActiveProjections(userIds)) {
            ProfileBindingSummary previous = result.put(projection.getUserId(), new ProfileBindingSummary(
                projection.getEnterpriseProfileId(), ProfileType.ENTERPRISE, projection.getVerifiedAt()));
            if (previous != null) {
                throw new IllegalStateException("Duplicate active enterprise projection");
            }
        }
        return Map.copyOf(result);
    }
}
