package org.dromara.profile.enterprise.service;

import org.dromara.profile.enterprise.domain.application.EnterpriseDocumentTypeRule;
import org.dromara.profile.enterprise.domain.application.EnterprisePublication;
import org.dromara.profile.enterprise.domain.application.EnterpriseSubmission;
import org.dromara.profile.enterprise.domain.vo.EnterpriseApplicationVo;
import org.dromara.profile.enterprise.domain.bo.EnterpriseApplicationSaveBo;
import org.dromara.profile.enterprise.domain.bo.EnterpriseApplicationProbeBo;
import org.dromara.profile.enterprise.domain.vo.EnterpriseApplicationProbeVo;

import java.util.Optional;

/**
 * 承载IEnterpriseApplicationService业务规则的领域服务。
 */
public interface IEnterpriseApplicationService extends org.dromara.profile.enterprise.port.EnterpriseApplicationPublicationPort {

    /**
     * 查询当前用户的进行中申请
     */
    Optional<EnterpriseApplicationVo> current(long userId);

    /**
     * 校验申请身份并返回探测结果
     */
    EnterpriseApplicationProbeVo probe(EnterpriseApplicationProbeBo command);

    /**
     * 保存业务申请数据
     */
    EnterpriseApplicationVo save(long userId, EnterpriseApplicationSaveBo command);

    /**
     * 提交申请并启动后续流程
     */
    EnterpriseApplicationVo submit(long userId, int expectedVersion);

}
