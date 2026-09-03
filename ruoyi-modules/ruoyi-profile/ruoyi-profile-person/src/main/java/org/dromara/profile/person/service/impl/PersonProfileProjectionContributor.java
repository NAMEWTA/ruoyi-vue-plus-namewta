package org.dromara.profile.person.service.impl;

import org.dromara.profile.person.domain.application.PersonActiveProjection;
import org.dromara.profile.person.dao.PersonApplicationDao;
import org.dromara.profile.api.ProfileProjectionContributor;
import org.dromara.profile.api.domain.ProfileBindingSummary;
import org.dromara.profile.api.domain.ProfileType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 创建个人档案投影处理器。
 */
@Component
public class PersonProfileProjectionContributor implements ProfileProjectionContributor {

    private final PersonApplicationDao dao;

    /**
     * 处理personprofileprojectioncontributor。
     */
    public PersonProfileProjectionContributor(PersonApplicationDao dao) {
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
     * 查询生效的绑定关系
     */
    @Override
    public Map<Long, ProfileBindingSummary> findActiveBindings(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ProfileBindingSummary> result = new LinkedHashMap<>();
        for (PersonActiveProjection projection : findActiveProjections(userIds)) {
            ProfileBindingSummary previous = result.put(projection.userId(), new ProfileBindingSummary(
                projection.personProfileId(), ProfileType.PERSON, projection.verifiedAt()));
            if (previous != null) {
                throw new IllegalStateException("Duplicate active person projection");
            }
        }
        return Map.copyOf(result);
    }

    /**
     * 查询生效档案投影
     */
    private java.util.List<PersonActiveProjection> findActiveProjections(Set<Long> userIds) {
        return dao.selectActiveProjections(userIds).stream().map(row -> new PersonActiveProjection(
            row.getUserId(), row.getPersonProfileId(), row.getVerifiedAt())).toList();
    }
}
