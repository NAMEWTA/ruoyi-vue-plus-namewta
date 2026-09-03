package org.dromara.profile.enterprise.usecase;

import org.dromara.profile.enterprise.domain.bo.EnterpriseApplicationProbeBo;
import org.dromara.profile.enterprise.domain.bo.EnterpriseApplicationSaveBo;
import org.dromara.profile.enterprise.domain.vo.EnterpriseApplicationProbeVo;
import org.dromara.profile.enterprise.domain.vo.EnterpriseApplicationVo;

/**
 * EnterpriseApplicationUseCase 应用用例合同，定义入口可调用的业务场景。
 */
public interface EnterpriseApplicationUseCase {
    /**
     * 编排 current 应用用例。
     */
    EnterpriseApplicationVo current();
    /**
     * 编排 save 应用用例。
     */
    EnterpriseApplicationVo save(EnterpriseApplicationSaveBo command);
    /**
     * 编排 submit 应用用例。
     */
    EnterpriseApplicationVo submit(int expectedVersion);
    /**
     * 编排 probe 应用用例。
     */
    EnterpriseApplicationProbeVo probe(EnterpriseApplicationProbeBo command);
}
