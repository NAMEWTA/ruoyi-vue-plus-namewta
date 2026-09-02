package org.dromara.profile.enterprise.controller.admin;

import org.dromara.profile.enterprise.service.IEnterpriseAdminService;
import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.profile.enterprise.domain.bo.*;
import org.dromara.profile.enterprise.domain.vo.*;
import org.dromara.system.api.OssService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/profile/enterprise/archive")
public class EnterpriseAdminController {

    private final IEnterpriseAdminService service;

    @GetMapping
    @SaCheckPermission("profile:enterprise:query")
    public R<PageResult<EnterpriseProfileSummaryVo>> page(EnterpriseAdminQueryBo query) {
        return R.ok(service.page(query));
    }

    @GetMapping("/eligible-users")
    @SaCheckPermission("profile:enterprise:override")
    public R<List<EnterpriseAccountCandidateVo>> eligibleUsers(@RequestParam String keyword) {
        return R.ok(service.eligibleUsers(keyword));
    }

    @GetMapping("/{profileId}")
    @SaCheckPermission("profile:enterprise:query")
    public R<EnterpriseProfileDetailVo> detail(@Positive @PathVariable long profileId) {
        return R.ok(service.detail(profileId));
    }

    @GetMapping("/application/{applicationId}/review-context")
    @SaCheckPermission("profile:enterprise:review")
    public R<EnterpriseReviewContextVo> reviewContext(@Positive @PathVariable long applicationId) {
        return R.ok(service.review(applicationId));
    }

    @GetMapping("/application/{applicationId}/material/{materialRefId}/access-url")
    @SaCheckPermission("profile:enterprise:review")
    public R<OssService.OssAccessUrl> reviewMaterial(@Positive @PathVariable long applicationId,
                                                     @Positive @PathVariable long materialRefId) {
        return R.ok(service.reviewMaterial(applicationId, materialRefId));
    }

    @GetMapping("/{profileId}/material/{materialRefId}/access-url")
    @SaCheckPermission("profile:enterprise:material")
    public R<OssService.OssAccessUrl> material(@Positive @PathVariable long profileId,
                                               @Positive @PathVariable long materialRefId) {
        return R.ok(service.material(profileId, materialRefId));
    }

    @PostMapping("/application/{applicationId}/decision")
    @SaCheckPermission("profile:enterprise:override")
    @Log(title = "企业档案管理员决定", businessType = BusinessType.OTHER,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<EnterpriseAdminResultVo> decide(@Positive @PathVariable long applicationId,
                                             @Valid @RequestBody EnterpriseAdminDecisionBo command) {
        return R.ok(service.decide(LoginHelper.getUserId(), applicationId, command));
    }

    @PostMapping("/admin-create")
    @SaCheckPermission("profile:enterprise:override")
    @Log(title = "管理员直建企业档案", businessType = BusinessType.INSERT,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<EnterpriseAdminResultVo> create(@Valid @RequestBody EnterpriseAdminCreateBo command) {
        return R.ok(service.create(LoginHelper.getUserId(), command));
    }

    @PostMapping("/{profileId}/revision")
    @SaCheckPermission("profile:enterprise:override")
    @Log(title = "管理员修订企业档案", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<EnterpriseAdminResultVo> revise(@Positive @PathVariable long profileId,
                                             @Valid @RequestBody EnterpriseAdminReviseBo command) {
        return R.ok(service.revise(LoginHelper.getUserId(), profileId, command));
    }

    @PostMapping("/{profileId}/assign")
    @SaCheckPermission("profile:enterprise:override")
    @Log(title = "管理员指定企业档案负责人", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<EnterpriseAdminResultVo> assign(@Positive @PathVariable long profileId,
                                             @Valid @RequestBody EnterpriseAdminAssignBo command) {
        return R.ok(service.assign(LoginHelper.getUserId(), profileId, command));
    }

    @PostMapping("/{profileId}/binding")
    @SaCheckPermission("profile:enterprise:manage")
    @Log(title = "企业档案绑定处置", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<EnterpriseAdminResultVo> manageBinding(@Positive @PathVariable long profileId,
                                                    @Valid @RequestBody EnterpriseAdminBindingBo command) {
        return R.ok(service.manageBinding(LoginHelper.getUserId(), profileId, command));
    }

    @PostMapping("/{profileId}/revoke")
    @SaCheckPermission("profile:enterprise:override")
    @Log(title = "注销企业档案", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<EnterpriseAdminResultVo> revoke(@Positive @PathVariable long profileId,
                                             @Valid @RequestBody EnterpriseAdminRevokeBo command) {
        return R.ok(service.revoke(LoginHelper.getUserId(), profileId, command));
    }
}
