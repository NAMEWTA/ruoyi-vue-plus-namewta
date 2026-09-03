package org.dromara.profile.person.usecase;

import org.dromara.common.core.domain.PageResult;
import org.dromara.profile.person.domain.bo.*;
import org.dromara.profile.person.domain.vo.*;
import org.dromara.system.api.OssService.OssAccessUrl;

import java.util.List;

/**
 * PersonAdminUseCase 应用用例合同，定义入口可调用的业务场景。
 */
public interface PersonAdminUseCase {

    /**
     * 编排 page 应用用例。
     */
    PageResult<PersonProfileSummaryVo> page(PersonAdminQueryBo query);
    /**
     * 编排 eligibleUsers 应用用例。
     */
    List<PersonAccountCandidateVo> eligibleUsers(String keyword);
    /**
     * 编排 detail 应用用例。
     */
    PersonProfileDetailVo detail(long profileId);
    /**
     * 编排 review 应用用例。
     */
    PersonReviewContextVo review(long applicationId);
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
    PersonAdminResultVo decide(long applicationId, PersonAdminDecisionBo command);
    /**
     * 编排 create 应用用例。
     */
    PersonAdminResultVo create(PersonAdminCreateBo command);
    /**
     * 编排 revise 应用用例。
     */
    PersonAdminResultVo revise(long profileId, PersonAdminReviseBo command);
    /**
     * 编排 manageBinding 应用用例。
     */
    PersonAdminResultVo manageBinding(long profileId, PersonAdminBindingBo command);
    /**
     * 编排 assign 应用用例。
     */
    PersonAdminResultVo assign(long profileId, PersonAdminAssignBo command);
    /**
     * 编排 revoke 应用用例。
     */
    PersonAdminResultVo revoke(long profileId, PersonAdminRevokeBo command);
}
