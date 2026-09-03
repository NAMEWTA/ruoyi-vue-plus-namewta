package org.dromara.profile.person.service.impl;

import org.dromara.profile.api.person.PersonIdentityLookupService;
import org.dromara.profile.person.domain.model.read.PersonActiveIdentityMatchRow;
import org.dromara.profile.person.dao.PersonApplicationDao;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 创建个人身份查询服务实现。
 */
@Service
public class PersonIdentityLookupServiceImpl implements PersonIdentityLookupService {

    private final PersonApplicationDao dao;

    /**
     * 处理personidentitylookupserviceimpl。
     */
    public PersonIdentityLookupServiceImpl(PersonApplicationDao dao) {
        this.dao = dao;
    }

    /**
     * 查询生效的精确身份匹配
     */
    @Override
    public List<ActiveIdentityMatch> findActiveExactMatches(ActiveIdentityQuery query) {
        return dao.selectActiveIdentityMatches(query.fullName(), query.documentLastFour()).stream()
            .map(this::match)
            .toList();
    }

    /**
     * 锁定生效的精确匹配记录
     */
    @Override
    public Optional<ActiveIdentityMatch> lockActiveExactMatch(ActiveIdentityLock query) {
        return Optional.ofNullable(dao.lockActiveIdentityMatch(
                query.userId(), query.personProfileId(), query.fullName(), query.documentLastFour()))
            .map(this::match);
    }

    /**
     * 匹配身份数据
     */
    private ActiveIdentityMatch match(PersonActiveIdentityMatchRow row) {
        return new ActiveIdentityMatch(row.getUserId(), row.getPersonProfileId());
    }
}
