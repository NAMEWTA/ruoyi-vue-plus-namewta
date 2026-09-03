package org.dromara.profile.enterprise.service.impl;

import org.dromara.profile.enterprise.dao.EnterpriseAdminDao;
import org.dromara.profile.enterprise.mapper.EnterpriseAdminMapper;
import org.dromara.profile.enterprise.service.EnterpriseAdminService;
import org.dromara.profile.enterprise.usecase.EnterpriseAdminUseCase;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.profile.enterprise.domain.bo.*;
import org.dromara.profile.enterprise.domain.vo.*;
import org.dromara.profile.enterprise.service.IEnterpriseApplicationService;
import org.dromara.profile.api.ProfileService;
import org.dromara.profile.api.material.ProfileMaterialPort;
import org.dromara.system.api.UserService;
import org.dromara.workflow.api.WorkflowService;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

public class EnterpriseAdminServiceImpl extends EnterpriseAdminService implements EnterpriseAdminUseCase {
    public EnterpriseAdminServiceImpl(EnterpriseAdminMapper mapper, JsonMapper jsonMapper,
                                       IEnterpriseApplicationService applications,
                                       ProfileMaterialPort materials, WorkflowService workflow, UserService users,
                                       ProfileService profiles, Clock clock) {
        super(new EnterpriseAdminDao(mapper), jsonMapper, applications, materials, workflow, users, profiles, clock);
    }

    @Override public EnterpriseAdminResultVo decide(long applicationId, EnterpriseAdminDecisionBo command) {
        return decide(LoginHelper.getUserId(), applicationId, command);
    }
    @Override public EnterpriseAdminResultVo create(EnterpriseAdminCreateBo command) {
        return create(LoginHelper.getUserId(), command);
    }
    @Override public EnterpriseAdminResultVo revise(long profileId, EnterpriseAdminReviseBo command) {
        return revise(LoginHelper.getUserId(), profileId, command);
    }
    @Override public EnterpriseAdminResultVo manageBinding(long profileId, EnterpriseAdminBindingBo command) {
        return manageBinding(LoginHelper.getUserId(), profileId, command);
    }
    @Override public EnterpriseAdminResultVo assign(long profileId, EnterpriseAdminAssignBo command) {
        return assign(LoginHelper.getUserId(), profileId, command);
    }
    @Override public EnterpriseAdminResultVo revoke(long profileId, EnterpriseAdminRevokeBo command) {
        return revoke(LoginHelper.getUserId(), profileId, command);
    }
}
