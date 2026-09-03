package org.dromara.profile.enterprise.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.profile.enterprise.domain.bo.EnterpriseApplicationProbeBo;
import org.dromara.profile.enterprise.domain.bo.EnterpriseApplicationSaveBo;
import org.dromara.profile.enterprise.domain.vo.EnterpriseApplicationProbeVo;
import org.dromara.profile.enterprise.domain.vo.EnterpriseApplicationVo;
import org.dromara.profile.enterprise.service.EnterpriseApplicationService;
import org.dromara.profile.enterprise.usecase.EnterpriseApplicationUseCase;
import org.springframework.stereotype.Service;

/**
 * EnterpriseApplicationUseCaseImpl 应用用例合同，定义入口可调用的业务场景。
 */
@Service
@RequiredArgsConstructor
public class EnterpriseApplicationUseCaseImpl implements EnterpriseApplicationUseCase {
    private final EnterpriseApplicationService service;
    /**
     * 编排 current 应用用例。
     */
    @Override public EnterpriseApplicationVo current() { return service.current(LoginHelper.getUserId()).orElse(null); }
    @Override public EnterpriseApplicationVo save(EnterpriseApplicationSaveBo command) {
        return service.save(LoginHelper.getUserId(), command);
    }
    /**
     * 编排 submit 应用用例。
     */
    @Override public EnterpriseApplicationVo submit(int expectedVersion) {
        return service.submit(LoginHelper.getUserId(), expectedVersion);
    }
    /**
     * 编排 probe 应用用例。
     */
    @Override public EnterpriseApplicationProbeVo probe(EnterpriseApplicationProbeBo command) {
        return service.probe(command);
    }
}
