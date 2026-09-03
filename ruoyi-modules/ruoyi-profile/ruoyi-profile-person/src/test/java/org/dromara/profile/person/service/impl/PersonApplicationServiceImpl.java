package org.dromara.profile.person.service.impl;

import org.dromara.profile.person.service.PersonVerificationAttemptService;

import org.dromara.profile.person.adapter.provider.PersonVerificationProviderRegistry;

import org.dromara.profile.person.dao.PersonApplicationDao;
import org.dromara.profile.person.mapper.PersonApplicationMapper;
import org.dromara.profile.person.service.PersonApplicationService;
import org.dromara.profile.person.service.IPersonApplicationService;
import org.dromara.profile.person.port.gateway.PersonWorkflowGateway;
import org.dromara.profile.person.usecase.PersonApplicationUseCase;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.profile.api.material.ProfileMaterialPort;
import org.dromara.system.api.ConfigService;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

/** 生产代码使用 PersonApplicationService，本类仅作为测试兼容适配器。 */
public class PersonApplicationServiceImpl extends PersonApplicationService
    implements PersonApplicationUseCase, IPersonApplicationService {
    public PersonApplicationServiceImpl(PersonApplicationMapper mapper, JsonMapper jsonMapper,
                                        ProfileMaterialPort materials, PersonVerificationProviderRegistry providers,
                                        PersonVerificationAttemptService attempts, PersonWorkflowGateway workflow,
                                        ConfigService configService, Clock clock) {
        super(new PersonApplicationDao(mapper), jsonMapper, materials, providers, attempts, workflow, configService, clock);
    }

    @Override public org.dromara.profile.person.domain.vo.PersonApplicationVo current() {
        return current(LoginHelper.getUserId());
    }

    @Override public org.dromara.profile.person.domain.vo.PersonApplicationVo save(
        org.dromara.profile.person.domain.bo.PersonApplicationSaveBo command) {
        return save(LoginHelper.getUserId(), command);
    }

    @Override public org.dromara.profile.person.domain.vo.PersonApplicationVo submit(int expectedVersion) {
        return submit(LoginHelper.getUserId(), expectedVersion);
    }
}
