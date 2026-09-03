package org.dromara.profile.enterprise.service.impl;

import org.dromara.profile.enterprise.dao.EnterpriseApplicationDao;
import org.dromara.profile.enterprise.mapper.EnterpriseApplicationMapper;
import org.dromara.profile.enterprise.service.EnterpriseApplicationService;
import org.dromara.profile.enterprise.service.IEnterpriseApplicationService;
import org.dromara.profile.enterprise.service.EnterpriseWorkflowGateway;
import org.dromara.profile.enterprise.usecase.EnterpriseApplicationUseCase;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.profile.api.material.ProfileMaterialPort;
import org.dromara.system.api.ConfigService;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

public class EnterpriseApplicationServiceImpl extends EnterpriseApplicationService
    implements EnterpriseApplicationUseCase, IEnterpriseApplicationService {
    public EnterpriseApplicationServiceImpl(EnterpriseApplicationMapper mapper, JsonMapper jsonMapper,
                                             ProfileMaterialPort materials,
                                             EnterpriseVerificationProviderRegistry providers,
                                             EnterpriseVerificationAttemptCoordinator attempts,
                                             EnterpriseWorkflowGateway workflow, ConfigService configService,
                                             Clock clock) {
        super(new EnterpriseApplicationDao(mapper), jsonMapper, materials, providers, attempts, workflow,
            configService, clock);
    }

    @Override public org.dromara.profile.enterprise.domain.vo.EnterpriseApplicationVo current() {
        return current(LoginHelper.getUserId()).orElse(null);
    }
    @Override public org.dromara.profile.enterprise.domain.vo.EnterpriseApplicationVo save(
        org.dromara.profile.enterprise.domain.bo.EnterpriseApplicationSaveBo command) {
        return save(LoginHelper.getUserId(), command);
    }
    @Override public org.dromara.profile.enterprise.domain.vo.EnterpriseApplicationVo submit(int expectedVersion) {
        return submit(LoginHelper.getUserId(), expectedVersion);
    }
}
