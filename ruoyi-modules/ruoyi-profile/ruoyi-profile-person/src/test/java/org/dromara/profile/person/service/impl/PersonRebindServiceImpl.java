package org.dromara.profile.person.service.impl;

import org.dromara.profile.person.dao.PersonApplicationDao;
import org.dromara.profile.person.dao.PersonRebindDao;
import org.dromara.profile.person.mapper.PersonApplicationMapper;
import org.dromara.profile.person.mapper.PersonRebindMapper;
import org.dromara.profile.person.service.PersonRebindService;
import org.dromara.profile.person.service.PersonWorkflowGateway;
import org.dromara.profile.person.usecase.PersonRebindUseCase;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.profile.api.material.ProfileMaterialPort;
import org.dromara.system.api.UserService;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

public class PersonRebindServiceImpl extends PersonRebindService implements PersonRebindUseCase {
    public PersonRebindServiceImpl(PersonRebindMapper mapper, PersonApplicationMapper applicationMapper,
                                   JsonMapper jsonMapper, ProfileMaterialPort materials,
                                   PersonVerificationProviderRegistry providers,
                                   PersonVerificationAttemptCoordinator attempts,
                                   PersonWorkflowGateway workflow, UserService users, Clock clock) {
        super(new PersonRebindDao(mapper), new PersonApplicationDao(applicationMapper), jsonMapper, materials,
            providers, attempts, workflow, users, clock);
    }

    @Override public org.dromara.profile.person.domain.vo.PersonRebindMatchVo match(
        org.dromara.profile.person.domain.bo.PersonRebindMatchBo command) {
        return match(LoginHelper.getUserId(), command);
    }

    @Override public org.dromara.profile.person.domain.vo.PersonRebindConfirmationVo confirm(
        org.dromara.profile.person.domain.bo.PersonRebindConfirmBo command) {
        return confirm(LoginHelper.getUserId(), command);
    }

    @Override public org.dromara.profile.person.domain.vo.PersonRebindSubmissionVo submit(
        org.dromara.profile.person.domain.bo.PersonRebindSubmitBo command) {
        return submit(LoginHelper.getUserId(), command);
    }

    @Override public org.dromara.profile.person.domain.vo.PersonRebindUnbindVo unbind() {
        return unbind(LoginHelper.getUserId());
    }

    @Override public java.util.Optional<org.dromara.profile.person.domain.application.PersonRebindPublication>
        publishApproved(long applicationId, int snapshotVersion, java.time.Instant finishedTime) {
        return publishApprovedRebind(applicationId, snapshotVersion, finishedTime);
    }
}
