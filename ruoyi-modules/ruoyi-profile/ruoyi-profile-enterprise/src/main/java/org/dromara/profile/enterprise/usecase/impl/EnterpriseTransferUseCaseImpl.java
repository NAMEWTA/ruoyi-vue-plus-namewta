package org.dromara.profile.enterprise.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.profile.enterprise.domain.bo.EnterpriseTransferConfirmBo;
import org.dromara.profile.enterprise.domain.bo.EnterpriseTransferSendBo;
import org.dromara.profile.enterprise.domain.vo.EnterpriseTransferVo;
import org.dromara.profile.enterprise.service.EnterpriseTransferService;
import org.dromara.profile.enterprise.usecase.EnterpriseTransferUseCase;
import org.springframework.stereotype.Service;

/**
 * EnterpriseTransferUseCaseImpl 应用用例合同，定义入口可调用的业务场景。
 */
@Service
@RequiredArgsConstructor
public class EnterpriseTransferUseCaseImpl implements EnterpriseTransferUseCase {
    private final EnterpriseTransferService service;
    /**
     * 编排 send 应用用例。
     */
    @Override public EnterpriseTransferVo send(EnterpriseTransferSendBo command) {
        return service.send(LoginHelper.getUserId(), command);
    }
    /**
     * 编排 confirm 应用用例。
     */
    @Override public EnterpriseTransferVo confirm(EnterpriseTransferConfirmBo command) {
        return service.confirm(LoginHelper.getUserId(), command);
    }
    /**
     * 编排 unbind 应用用例。
     */
    @Override public EnterpriseTransferVo unbind() { return service.unbind(LoginHelper.getUserId()); }
}
