package org.dromara.profile.person.service.impl;

import org.dromara.profile.person.service.PersonVerificationAttemptService;

import org.dromara.profile.person.adapter.provider.PersonVerificationProviderRegistry;

import org.dromara.profile.person.dao.PersonApplicationDao;
import org.dromara.profile.person.dao.PersonRebindDao;
import org.dromara.profile.person.mapper.PersonApplicationMapper;
import org.dromara.profile.person.mapper.PersonRebindMapper;
import org.dromara.profile.person.service.PersonRebindService;
import org.dromara.profile.person.port.notification.PersonRebindNotificationPort;
import org.dromara.profile.person.port.gateway.PersonWorkflowGateway;
import org.dromara.profile.person.usecase.PersonRebindUseCase;
import org.dromara.profile.person.domain.application.PersonRebindProcessCommand;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.profile.api.material.ProfileMaterialPort;
import org.dromara.system.api.UserService;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

public class PersonRebindServiceImpl extends PersonRebindService implements PersonRebindUseCase {
    public PersonRebindServiceImpl(PersonRebindMapper mapper, PersonApplicationMapper applicationMapper,
                                   JsonMapper jsonMapper, ProfileMaterialPort materials,
                                   PersonVerificationProviderRegistry providers,
                                   PersonVerificationAttemptService attempts,
                                   PersonWorkflowGateway workflow, UserService users, Clock clock) {
        super(new PersonRebindDao(mapper), new PersonApplicationDao(applicationMapper), jsonMapper, materials,
            providers, attempts, workflow, users, clock);
    }

    /** 创建带工作流事件副作用的测试适配器。 */
    public PersonRebindServiceImpl(PersonRebindMapper mapper, PersonApplicationMapper applicationMapper,
                                   JsonMapper jsonMapper, ProfileMaterialPort materials,
                                   PersonVerificationProviderRegistry providers,
                                   PersonVerificationAttemptService attempts,
                                   PersonWorkflowGateway workflow, UserService users, Clock clock,
                                   org.dromara.system.api.ConfigService configService,
                                   PersonRebindNotificationPort notifications,
                                   org.springframework.context.ApplicationEventPublisher events) {
        super(new PersonRebindDao(mapper), new PersonApplicationDao(applicationMapper), jsonMapper, materials,
            providers, attempts, workflow, users, configService, notifications, events);
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

    /** 兼容测试适配器，将工作流命令交给实际服务实现。 */
    @Override public void handleProcess(PersonRebindProcessCommand command) {
        super.handleProcess(command);
    }
}
