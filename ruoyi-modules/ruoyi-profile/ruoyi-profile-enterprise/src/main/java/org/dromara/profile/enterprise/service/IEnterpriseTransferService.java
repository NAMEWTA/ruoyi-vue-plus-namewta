package org.dromara.profile.enterprise.service;

import org.dromara.profile.enterprise.domain.bo.EnterpriseTransferConfirmBo;
import org.dromara.profile.enterprise.domain.bo.EnterpriseTransferSendBo;
import org.dromara.profile.enterprise.domain.vo.EnterpriseTransferVo;

public interface IEnterpriseTransferService {

    EnterpriseTransferVo send(long sourceUserId, EnterpriseTransferSendBo command);

    EnterpriseTransferVo confirm(long sourceUserId, EnterpriseTransferConfirmBo command);

    EnterpriseTransferVo unbind(long userId);
}
