package org.dromara.profile.enterprise.service;

import org.dromara.common.core.domain.PageResult;
import org.dromara.profile.enterprise.domain.vo.EnterpriseAccountCandidateVo;
import org.dromara.profile.enterprise.domain.bo.EnterpriseAdminAssignBo;
import org.dromara.profile.enterprise.domain.bo.EnterpriseAdminBindingBo;
import org.dromara.profile.enterprise.domain.bo.EnterpriseAdminCreateBo;
import org.dromara.profile.enterprise.domain.bo.EnterpriseAdminDecisionBo;
import org.dromara.profile.enterprise.domain.vo.EnterpriseProfileDetailVo;
import org.dromara.profile.enterprise.domain.bo.EnterpriseAdminQueryBo;
import org.dromara.profile.enterprise.domain.vo.EnterpriseAdminResultVo;
import org.dromara.profile.enterprise.domain.vo.EnterpriseReviewContextVo;
import org.dromara.profile.enterprise.domain.bo.EnterpriseAdminReviseBo;
import org.dromara.profile.enterprise.domain.bo.EnterpriseAdminRevokeBo;
import org.dromara.profile.enterprise.domain.vo.EnterpriseProfileSummaryVo;
import org.dromara.system.api.OssService.OssAccessUrl;

import java.util.List;

public interface IEnterpriseAdminService {

    PageResult<EnterpriseProfileSummaryVo> page(EnterpriseAdminQueryBo query);

    List<EnterpriseAccountCandidateVo> eligibleUsers(String keyword);

    EnterpriseProfileDetailVo detail(long profileId);

    EnterpriseReviewContextVo review(long applicationId);

    OssAccessUrl reviewMaterial(long applicationId, long materialRefId);

    OssAccessUrl material(long profileId, long materialRefId);

    EnterpriseAdminResultVo decide(long operatorId, long applicationId, EnterpriseAdminDecisionBo command);

    EnterpriseAdminResultVo create(long operatorId, EnterpriseAdminCreateBo command);

    EnterpriseAdminResultVo revise(long operatorId, long profileId, EnterpriseAdminReviseBo command);

    EnterpriseAdminResultVo manageBinding(long operatorId, long profileId, EnterpriseAdminBindingBo command);

    EnterpriseAdminResultVo assign(long operatorId, long profileId, EnterpriseAdminAssignBo command);

    EnterpriseAdminResultVo revoke(long operatorId, long profileId, EnterpriseAdminRevokeBo command);
}
