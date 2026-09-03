package org.dromara.profile.enterprise.usecase;

import org.dromara.profile.enterprise.domain.bo.EnterpriseTransferConfirmBo;
import org.dromara.profile.enterprise.domain.bo.EnterpriseTransferSendBo;
import org.dromara.profile.enterprise.domain.vo.EnterpriseTransferVo;

/**
 * EnterpriseTransferUseCase 应用用例合同，定义入口可调用的业务场景。
 */
public interface EnterpriseTransferUseCase {
    /**
     * 编排 send 应用用例。
     */
    EnterpriseTransferVo send(EnterpriseTransferSendBo command);
    /**
     * 编排 confirm 应用用例。
     */
    EnterpriseTransferVo confirm(EnterpriseTransferConfirmBo command);
    /**
     * 编排 unbind 应用用例。
     */
    EnterpriseTransferVo unbind();
}
