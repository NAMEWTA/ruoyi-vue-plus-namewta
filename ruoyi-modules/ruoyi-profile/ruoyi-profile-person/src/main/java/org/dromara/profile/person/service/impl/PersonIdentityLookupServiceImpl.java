package org.dromara.profile.person.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.profile.api.person.PersonIdentityLookupService;
import org.dromara.profile.person.domain.vo.PersonActiveIdentityMatchRow;
import org.dromara.profile.person.mapper.PersonApplicationMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PersonIdentityLookupServiceImpl implements PersonIdentityLookupService {

    private final PersonApplicationMapper mapper;

    @Override
    public List<ActiveIdentityMatch> findActiveExactMatches(ActiveIdentityQuery query) {
        return mapper.selectActiveIdentityMatches(query.fullName(), query.documentLastFour()).stream()
            .map(this::match)
            .toList();
    }

    @Override
    public Optional<ActiveIdentityMatch> lockActiveExactMatch(ActiveIdentityLock query) {
        return Optional.ofNullable(mapper.lockActiveIdentityMatch(
                query.userId(), query.personProfileId(), query.fullName(), query.documentLastFour()))
            .map(this::match);
    }

    private ActiveIdentityMatch match(PersonActiveIdentityMatchRow row) {
        return new ActiveIdentityMatch(row.getUserId(), row.getPersonProfileId());
    }
}
