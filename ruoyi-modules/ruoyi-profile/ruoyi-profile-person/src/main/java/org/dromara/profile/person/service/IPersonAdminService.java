package org.dromara.profile.person.service;

import org.dromara.common.core.domain.PageResult;
import org.dromara.profile.person.domain.vo.PersonAccountCandidateVo;
import org.dromara.profile.person.domain.bo.PersonAdminAssignBo;
import org.dromara.profile.person.domain.bo.PersonAdminBindingBo;
import org.dromara.profile.person.domain.bo.PersonAdminCreateBo;
import org.dromara.profile.person.domain.bo.PersonAdminDecisionBo;
import org.dromara.profile.person.domain.vo.PersonProfileDetailVo;
import org.dromara.profile.person.domain.bo.PersonAdminQueryBo;
import org.dromara.profile.person.domain.vo.PersonAdminResultVo;
import org.dromara.profile.person.domain.vo.PersonReviewContextVo;
import org.dromara.profile.person.domain.bo.PersonAdminReviseBo;
import org.dromara.profile.person.domain.bo.PersonAdminRevokeBo;
import org.dromara.profile.person.domain.vo.PersonProfileSummaryVo;
import org.dromara.system.api.OssService.OssAccessUrl;

import java.util.List;

public interface IPersonAdminService {

    PageResult<PersonProfileSummaryVo> page(PersonAdminQueryBo query);

    List<PersonAccountCandidateVo> eligibleUsers(String keyword);

    PersonProfileDetailVo detail(long profileId);

    PersonReviewContextVo review(long applicationId);

    OssAccessUrl reviewMaterial(long applicationId, long materialRefId);

    OssAccessUrl material(long profileId, long materialRefId);

    PersonAdminResultVo decide(long operatorId, long applicationId, PersonAdminDecisionBo command);

    PersonAdminResultVo create(long operatorId, PersonAdminCreateBo command);

    PersonAdminResultVo revise(long operatorId, long profileId, PersonAdminReviseBo command);

    PersonAdminResultVo manageBinding(long operatorId, long profileId, PersonAdminBindingBo command);

    PersonAdminResultVo assign(long operatorId, long profileId, PersonAdminAssignBo command);

    PersonAdminResultVo revoke(long operatorId, long profileId, PersonAdminRevokeBo command);
}
