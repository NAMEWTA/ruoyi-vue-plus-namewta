package org.dromara.profile.person.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.profile.person.domain.application.PersonRebindPublication;
import org.dromara.profile.person.domain.bo.*;
import org.dromara.profile.person.domain.vo.*;
import org.dromara.profile.person.service.PersonRebindService;
import org.dromara.profile.person.usecase.PersonRebindUseCase;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * PersonRebindUseCaseImpl 应用用例合同，定义入口可调用的业务场景。
 */
@Service
@RequiredArgsConstructor
public class PersonRebindUseCaseImpl implements PersonRebindUseCase {

    private final PersonRebindService service;

    /**
     * 编排 probe 应用用例。
     */
    @Override public PersonRebindProbeVo probe(PersonRebindProbeBo command) { return service.probe(command); }
    @Override public PersonRebindMatchVo match(PersonRebindMatchBo command) {
        return service.match(LoginHelper.getUserId(), command);
    }
    /**
     * 编排 confirm 应用用例。
     */
    @Override public PersonRebindConfirmationVo confirm(PersonRebindConfirmBo command) {
        return service.confirm(LoginHelper.getUserId(), command);
    }
    /**
     * 编排 submit 应用用例。
     */
    @Override public PersonRebindSubmissionVo submit(PersonRebindSubmitBo command) {
        return service.submit(LoginHelper.getUserId(), command);
    }
    /**
     * 编排 unbind 应用用例。
     */
    @Override public PersonRebindUnbindVo unbind() { return service.unbind(LoginHelper.getUserId()); }
    @Override public Optional<PersonRebindPublication> publishApproved(long applicationId, int snapshotVersion,
                                                                         Instant finishedTime) {
        return service.publishApprovedRebind(applicationId, snapshotVersion, finishedTime);
    }
}
