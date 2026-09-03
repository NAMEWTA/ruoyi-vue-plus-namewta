package org.dromara.profile.person.service.impl;

import org.dromara.profile.person.dao.PersonAdminDao;
import org.dromara.profile.person.mapper.PersonAdminMapper;
import org.dromara.profile.person.domain.bo.*;
import org.dromara.profile.person.domain.vo.*;
import org.dromara.profile.person.service.IPersonApplicationService;
import org.dromara.profile.person.service.PersonAdminService;
import org.dromara.profile.person.usecase.PersonAdminUseCase;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.profile.api.material.ProfileMaterialPort;
import org.dromara.system.api.UserService;
import org.dromara.workflow.api.WorkflowService;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

public class PersonAdminServiceImpl extends PersonAdminService implements PersonAdminUseCase {
    public PersonAdminServiceImpl(PersonAdminMapper mapper, JsonMapper jsonMapper,
                                  IPersonApplicationService applications, ProfileMaterialPort materials,
                                  WorkflowService workflow, UserService users, Clock clock) {
        super(new PersonAdminDao(mapper), jsonMapper, applications, materials, workflow, users, clock);
    }
    public PersonAdminServiceImpl(PersonAdminMapper mapper, JsonMapper jsonMapper,
                                  IPersonApplicationService applications, ProfileMaterialPort materials,
                                  WorkflowService workflow, UserService users) {
        super(new PersonAdminDao(mapper), jsonMapper, applications, materials, workflow, users);
    }

    @Override public PersonAdminResultVo decide(long applicationId, PersonAdminDecisionBo command) {
        return decide(LoginHelper.getUserId(), applicationId, command);
    }
    @Override public PersonAdminResultVo create(PersonAdminCreateBo command) {
        return create(LoginHelper.getUserId(), command);
    }
    @Override public PersonAdminResultVo revise(long profileId, PersonAdminReviseBo command) {
        return revise(LoginHelper.getUserId(), profileId, command);
    }
    @Override public PersonAdminResultVo manageBinding(long profileId, PersonAdminBindingBo command) {
        return manageBinding(LoginHelper.getUserId(), profileId, command);
    }
    @Override public PersonAdminResultVo assign(long profileId, PersonAdminAssignBo command) {
        return assign(LoginHelper.getUserId(), profileId, command);
    }
    @Override public PersonAdminResultVo revoke(long profileId, PersonAdminRevokeBo command) {
        return revoke(LoginHelper.getUserId(), profileId, command);
    }
}
