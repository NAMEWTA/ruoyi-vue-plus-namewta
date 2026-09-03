package org.dromara.profile.enterprise.service.impl;

import org.dromara.common.notify.core.NotifyClient;
import org.dromara.profile.api.person.PersonIdentityLookupService;
import org.dromara.profile.enterprise.dao.EnterpriseTransferDao;
import org.dromara.profile.enterprise.mapper.EnterpriseTransferMapper;
import org.dromara.profile.enterprise.service.EnterpriseTransferChallengeStore;
import org.dromara.profile.enterprise.service.EnterpriseTransferService;
import org.dromara.profile.enterprise.usecase.EnterpriseTransferUseCase;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.api.UserService;

import java.time.Clock;

public class EnterpriseTransferServiceImpl extends EnterpriseTransferService implements EnterpriseTransferUseCase {
    public EnterpriseTransferServiceImpl(EnterpriseTransferMapper mapper, EnterpriseTransferChallengeStore challenges,
                                          EnterpriseTransferCodeGenerator codes,
                                          PersonIdentityLookupService personIdentities, UserService users,
                                          NotifyClient notify, Clock clock) {
        super(new EnterpriseTransferDao(mapper), challenges, codes, personIdentities, users, notify, clock);
    }

    @Override public org.dromara.profile.enterprise.domain.vo.EnterpriseTransferVo send(
        org.dromara.profile.enterprise.domain.bo.EnterpriseTransferSendBo command) {
        return send(LoginHelper.getUserId(), command);
    }
    @Override public org.dromara.profile.enterprise.domain.vo.EnterpriseTransferVo confirm(
        org.dromara.profile.enterprise.domain.bo.EnterpriseTransferConfirmBo command) {
        return confirm(LoginHelper.getUserId(), command);
    }
    @Override public org.dromara.profile.enterprise.domain.vo.EnterpriseTransferVo unbind() {
        return unbind(LoginHelper.getUserId());
    }
}
