package org.dromara.profile.person.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.core.domain.PageResult;
import org.dromara.profile.person.domain.bo.*;
import org.dromara.profile.person.domain.vo.*;
import org.dromara.profile.person.service.PersonAdminService;
import org.dromara.profile.person.usecase.PersonAdminUseCase;
import org.dromara.system.api.OssService.OssAccessUrl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * PersonAdminUseCaseImpl 应用用例合同，定义入口可调用的业务场景。
 */
@Service
@RequiredArgsConstructor
public class PersonAdminUseCaseImpl implements PersonAdminUseCase {

    private final PersonAdminService service;

    /**
     * 编排 page 应用用例。
     */
    @Override public PageResult<PersonProfileSummaryVo> page(PersonAdminQueryBo query) { return service.page(query); }
    @Override public List<PersonAccountCandidateVo> eligibleUsers(String keyword) { return service.eligibleUsers(keyword); }
    @Override public PersonProfileDetailVo detail(long profileId) { return service.detail(profileId); }
    @Override public PersonReviewContextVo review(long applicationId) { return service.review(applicationId); }
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
    @Override public PersonAdminResultVo decide(long applicationId, PersonAdminDecisionBo command) {
        return service.decide(LoginHelper.getUserId(), applicationId, command);
    }
    /**
     * 编排 create 应用用例。
     */
    @Override public PersonAdminResultVo create(PersonAdminCreateBo command) {
        return service.create(LoginHelper.getUserId(), command);
    }
    /**
     * 编排 revise 应用用例。
     */
    @Override public PersonAdminResultVo revise(long profileId, PersonAdminReviseBo command) {
        return service.revise(LoginHelper.getUserId(), profileId, command);
    }
    /**
     * 编排 manageBinding 应用用例。
     */
    @Override public PersonAdminResultVo manageBinding(long profileId, PersonAdminBindingBo command) {
        return service.manageBinding(LoginHelper.getUserId(), profileId, command);
    }
    /**
     * 编排 assign 应用用例。
     */
    @Override public PersonAdminResultVo assign(long profileId, PersonAdminAssignBo command) {
        return service.assign(LoginHelper.getUserId(), profileId, command);
    }
    /**
     * 编排 revoke 应用用例。
     */
    @Override public PersonAdminResultVo revoke(long profileId, PersonAdminRevokeBo command) {
        return service.revoke(LoginHelper.getUserId(), profileId, command);
    }
}
