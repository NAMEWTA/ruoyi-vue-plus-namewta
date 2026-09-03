package org.dromara.profile.enterprise.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.profile.enterprise.domain.bo.*;
import org.dromara.profile.enterprise.domain.vo.*;
import org.dromara.profile.enterprise.service.EnterpriseAdminService;
import org.dromara.profile.enterprise.usecase.EnterpriseAdminUseCase;
import org.dromara.system.api.OssService.OssAccessUrl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * EnterpriseAdminUseCaseImpl 应用用例合同，定义入口可调用的业务场景。
 */
@Service
@RequiredArgsConstructor
public class EnterpriseAdminUseCaseImpl implements EnterpriseAdminUseCase {
    private final EnterpriseAdminService service;
    /**
     * 编排 page 应用用例。
     */
    @Override public PageResult<EnterpriseProfileSummaryVo> page(EnterpriseAdminQueryBo query) { return service.page(query); }
    @Override public List<EnterpriseAccountCandidateVo> eligibleUsers(String keyword) { return service.eligibleUsers(keyword); }
    @Override public EnterpriseProfileDetailVo detail(long profileId) { return service.detail(profileId); }
    @Override public EnterpriseReviewContextVo review(long applicationId) { return service.review(applicationId); }
    @Override public OssAccessUrl reviewMaterial(long applicationId, long materialRefId) {
        return service.reviewMaterial(applicationId, materialRefId);
    }
    /**
     * 编排 material 应用用例。
     */
    @Override public OssAccessUrl material(long profileId, long materialRefId) {
        return service.material(profileId, materialRefId);
    }
    /**
     * 编排 decide 应用用例。
     */
    @Override public EnterpriseAdminResultVo decide(long applicationId, EnterpriseAdminDecisionBo command) {
        return service.decide(LoginHelper.getUserId(), applicationId, command);
    }
    /**
     * 编排 create 应用用例。
     */
    @Override public EnterpriseAdminResultVo create(EnterpriseAdminCreateBo command) {
        return service.create(LoginHelper.getUserId(), command);
    }
    /**
     * 编排 revise 应用用例。
     */
    @Override public EnterpriseAdminResultVo revise(long profileId, EnterpriseAdminReviseBo command) {
        return service.revise(LoginHelper.getUserId(), profileId, command);
    }
    /**
     * 编排 manageBinding 应用用例。
     */
    @Override public EnterpriseAdminResultVo manageBinding(long profileId, EnterpriseAdminBindingBo command) {
        return service.manageBinding(LoginHelper.getUserId(), profileId, command);
    }
    /**
     * 编排 assign 应用用例。
     */
    @Override public EnterpriseAdminResultVo assign(long profileId, EnterpriseAdminAssignBo command) {
        return service.assign(LoginHelper.getUserId(), profileId, command);
    }
    /**
     * 编排 revoke 应用用例。
     */
    @Override public EnterpriseAdminResultVo revoke(long profileId, EnterpriseAdminRevokeBo command) {
        return service.revoke(LoginHelper.getUserId(), profileId, command);
    }
}
