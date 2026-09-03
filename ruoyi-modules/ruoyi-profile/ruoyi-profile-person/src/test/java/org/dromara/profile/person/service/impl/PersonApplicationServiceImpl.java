package org.dromara.profile.person.service.impl;

import org.dromara.profile.person.dao.PersonApplicationDao;
import org.dromara.profile.person.mapper.PersonApplicationMapper;
import org.dromara.profile.person.service.PersonApplicationService;
import org.dromara.profile.person.service.IPersonApplicationService;
import org.dromara.profile.person.service.PersonWorkflowGateway;
import org.dromara.profile.person.usecase.PersonApplicationUseCase;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.profile.api.material.ProfileMaterialPort;
import org.dromara.system.api.ConfigService;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

/** Test compatibility facade while production uses PersonApplicationService. */
public class PersonApplicationServiceImpl extends PersonApplicationService
    implements PersonApplicationUseCase, IPersonApplicationService {
    public PersonApplicationServiceImpl(PersonApplicationMapper mapper, JsonMapper jsonMapper,
                                        ProfileMaterialPort materials, PersonVerificationProviderRegistry providers,
                                        PersonVerificationAttemptCoordinator attempts, PersonWorkflowGateway workflow,
                                        ConfigService configService, Clock clock) {
        super(new PersonApplicationDao(mapper), jsonMapper, materials, providers, attempts, workflow, configService, clock);
    }

    @Override public org.dromara.profile.person.domain.vo.PersonApplicationVo current() {
        return current(LoginHelper.getUserId()).orElse(null);
    }

    @Override public org.dromara.profile.person.domain.vo.PersonApplicationVo save(
        org.dromara.profile.person.domain.bo.PersonApplicationSaveBo command) {
        return save(LoginHelper.getUserId(), command);
    }

    @Override public org.dromara.profile.person.domain.vo.PersonApplicationVo submit(int expectedVersion) {
        return submit(LoginHelper.getUserId(), expectedVersion);
    }
}
