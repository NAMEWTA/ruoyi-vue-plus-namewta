package org.dromara.profile.person.service.impl;

import org.dromara.profile.api.person.PersonIdentityLookupService.ActiveIdentityLock;
import org.dromara.profile.api.person.PersonIdentityLookupService.ActiveIdentityMatch;
import org.dromara.profile.api.person.PersonIdentityLookupService.ActiveIdentityQuery;
import org.dromara.profile.person.domain.model.read.PersonActiveIdentityMatchRow;
import org.dromara.profile.person.dao.PersonApplicationDao;
import org.dromara.profile.person.mapper.PersonApplicationMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class PersonIdentityLookupServiceImplTest {

    private final PersonApplicationMapper mapper = mock(PersonApplicationMapper.class);
    private final PersonIdentityLookupServiceImpl service = new PersonIdentityLookupServiceImpl(
        new PersonApplicationDao(mapper));

    @Test
    void mapsExactMatchesWithoutExposingIdentityFields() {
        PersonActiveIdentityMatchRow row = row(41L, 91L);
        when(mapper.selectActiveIdentityMatches("张三", "AB12")).thenReturn(List.of(row));

        assertThat(service.findActiveExactMatches(new ActiveIdentityQuery(" 张三 ", "ab12")))
            .containsExactly(new ActiveIdentityMatch(41L, 91L));

        verify(mapper).selectActiveIdentityMatches("张三", "AB12");
    }

    @Test
    void locksAndRechecksBothCandidateIdentifiers() {
        PersonActiveIdentityMatchRow row = row(42L, 92L);
        when(mapper.lockActiveIdentityMatch(42L, 92L, "李四", "7788")).thenReturn(row);

        assertThat(service.lockActiveExactMatch(new ActiveIdentityLock(42L, 92L, "李四", "7788")))
            .contains(new ActiveIdentityMatch(42L, 92L));
        assertThat(service.lockActiveExactMatch(new ActiveIdentityLock(43L, 93L, "王五", "9900")))
            .isEmpty();
    }

    private PersonActiveIdentityMatchRow row(long userId, long profileId) {
        PersonActiveIdentityMatchRow row = new PersonActiveIdentityMatchRow();
        row.setUserId(userId);
        row.setPersonProfileId(profileId);
        return row;
    }
}
