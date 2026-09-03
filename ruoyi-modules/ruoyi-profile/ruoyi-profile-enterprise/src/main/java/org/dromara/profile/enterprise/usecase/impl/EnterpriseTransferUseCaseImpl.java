package org.dromara.profile.enterprise.usecase.impl;

import com.baomidou.dynamic.datasource.annotation.DSTransactional;

import lombok.RequiredArgsConstructor;
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
    /** 发起企业转移申请。 */
    @DSTransactional
    @Override
    public EnterpriseTransferVo send(long userId, EnterpriseTransferSendBo command) {
        return service.send(userId, command);
    }
    /** 确认企业转移申请。 */
    @DSTransactional
    @Override
    public EnterpriseTransferVo confirm(long userId, EnterpriseTransferConfirmBo command) {
        return service.confirm(userId, command);
    }
    /** 解除企业当前绑定关系。 */
    @DSTransactional
    @Override
    public EnterpriseTransferVo unbind(long userId) {
        return service.unbind(userId);
    }
}
