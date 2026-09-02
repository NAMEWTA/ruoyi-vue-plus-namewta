package org.dromara.profile.person.controller.admin;

import jakarta.validation.Valid;

import org.dromara.profile.person.service.IPersonAdminService;
import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.profile.person.domain.bo.*;
import org.dromara.profile.person.domain.vo.*;
import org.dromara.system.api.OssService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/profile/person/archive")
public class PersonAdminController {

    private final IPersonAdminService service;

    @GetMapping
    @SaCheckPermission("profile:person:query")
    public R<PageResult<PersonProfileSummaryVo>> page(PersonAdminQueryBo query) {
        return R.ok(service.page(query));
    }

    @GetMapping("/eligible-users")
    @SaCheckPermission("profile:person:override")
    public R<List<PersonAccountCandidateVo>> eligibleUsers(@RequestParam String keyword) {
        return R.ok(service.eligibleUsers(keyword));
    }

    @GetMapping("/{profileId}")
    @SaCheckPermission("profile:person:query")
    public R<PersonProfileDetailVo> detail(@PathVariable long profileId) {
        return R.ok(service.detail(profileId));
    }

    @GetMapping("/application/{applicationId}/review-context")
    @SaCheckPermission("profile:person:review")
    public R<PersonReviewContextVo> reviewContext(@PathVariable long applicationId) {
        return R.ok(service.review(applicationId));
    }

    @GetMapping("/application/{applicationId}/material/{materialRefId}/access-url")
    @SaCheckPermission("profile:person:review")
    public R<OssService.OssAccessUrl> reviewMaterial(@PathVariable long applicationId,
                                                     @PathVariable long materialRefId) {
        return R.ok(service.reviewMaterial(applicationId, materialRefId));
    }

    @GetMapping("/{profileId}/material/{materialRefId}/access-url")
    @SaCheckPermission("profile:person:material")
    public R<OssService.OssAccessUrl> material(@PathVariable long profileId, @PathVariable long materialRefId) {
        return R.ok(service.material(profileId, materialRefId));
    }

    @PostMapping("/application/{applicationId}/decision")
    @SaCheckPermission("profile:person:override")
    @Log(title = "个人档案管理员决定", businessType = BusinessType.OTHER,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<PersonAdminResultVo> decide(@PathVariable long applicationId, @Valid @RequestBody PersonAdminDecisionBo command) {
        return R.ok(service.decide(LoginHelper.getUserId(), applicationId, command));
    }

    @PostMapping("/admin-create")
    @SaCheckPermission("profile:person:override")
    @Log(title = "管理员直建个人档案", businessType = BusinessType.INSERT,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<PersonAdminResultVo> create(@Valid @RequestBody PersonAdminCreateBo command) {
        return R.ok(service.create(LoginHelper.getUserId(), command));
    }

    @PostMapping("/{profileId}/revision")
    @SaCheckPermission("profile:person:override")
    @Log(title = "管理员修订个人档案", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<PersonAdminResultVo> revise(@PathVariable long profileId, @Valid @RequestBody PersonAdminReviseBo command) {
        return R.ok(service.revise(LoginHelper.getUserId(), profileId, command));
    }

    @PostMapping("/{profileId}/assign")
    @SaCheckPermission("profile:person:override")
    @Log(title = "管理员指定个人档案账户", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<PersonAdminResultVo> assign(@PathVariable long profileId, @Valid @RequestBody PersonAdminAssignBo command) {
        return R.ok(service.assign(LoginHelper.getUserId(), profileId, command));
    }

    @PostMapping("/{profileId}/binding")
    @SaCheckPermission("profile:person:manage")
    @Log(title = "个人档案绑定处置", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<PersonAdminResultVo> manageBinding(@PathVariable long profileId, @Valid @RequestBody PersonAdminBindingBo command) {
        return R.ok(service.manageBinding(LoginHelper.getUserId(), profileId, command));
    }

    @PostMapping("/{profileId}/revoke")
    @SaCheckPermission("profile:person:override")
    @Log(title = "注销个人档案", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<PersonAdminResultVo> revoke(@PathVariable long profileId, @Valid @RequestBody PersonAdminRevokeBo command) {
        return R.ok(service.revoke(LoginHelper.getUserId(), profileId, command));
    }
}
