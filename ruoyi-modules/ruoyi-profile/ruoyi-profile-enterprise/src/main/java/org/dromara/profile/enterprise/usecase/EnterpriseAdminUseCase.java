package org.dromara.profile.enterprise.usecase;

import org.dromara.common.core.domain.PageResult;
import org.dromara.profile.enterprise.domain.bo.*;
import org.dromara.profile.enterprise.domain.vo.*;
import org.dromara.system.api.OssService.OssAccessUrl;

import java.util.List;

/**
 * EnterpriseAdminUseCase 应用用例合同，定义入口可调用的业务场景。
 */
public interface EnterpriseAdminUseCase {
    /**
     * 编排 page 应用用例。
     */
    PageResult<EnterpriseProfileSummaryVo> page(EnterpriseAdminQueryBo query);
    /**
     * 编排 eligibleUsers 应用用例。
     */
    List<EnterpriseAccountCandidateVo> eligibleUsers(String keyword);
    /**
     * 编排 detail 应用用例。
     */
    EnterpriseProfileDetailVo detail(long profileId);
    /**
     * 编排 review 应用用例。
     */
    EnterpriseReviewContextVo review(long applicationId);
    /**
     * 编排 reviewMaterial 应用用例。
     */
    OssAccessUrl reviewMaterial(long applicationId, long materialRefId);
    /**
     * 编排 material 应用用例。
     */
    OssAccessUrl material(long profileId, long materialRefId);
    /**
     * 编排 decide 应用用例。
     */
    EnterpriseAdminResultVo decide(long applicationId, EnterpriseAdminDecisionBo command);
    /**
     * 编排 create 应用用例。
     */
    EnterpriseAdminResultVo create(EnterpriseAdminCreateBo command);
    /**
     * 编排 revise 应用用例。
     */
    EnterpriseAdminResultVo revise(long profileId, EnterpriseAdminReviseBo command);
    /**
     * 编排 manageBinding 应用用例。
     */
    EnterpriseAdminResultVo manageBinding(long profileId, EnterpriseAdminBindingBo command);
    /**
     * 编排 assign 应用用例。
     */
    EnterpriseAdminResultVo assign(long profileId, EnterpriseAdminAssignBo command);
    /**
     * 编排 revoke 应用用例。
     */
    EnterpriseAdminResultVo revoke(long profileId, EnterpriseAdminRevokeBo command);
}
