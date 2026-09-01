package org.dromara.profile.enterprise.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.profile.enterprise.admin.EnterpriseAdminContracts.*;
import org.dromara.system.api.OssService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/profile/enterprise/archive")
public class EnterpriseAdminController {

    private final EnterpriseAdminService service;

    @GetMapping
    @SaCheckPermission("profile:enterprise:query")
    public R<PageResult<Summary>> page(Query query) {
        return R.ok(service.page(query));
    }

    @GetMapping("/{profileId}")
    @SaCheckPermission("profile:enterprise:query")
    public R<Detail> detail(@PathVariable long profileId) {
        return R.ok(service.detail(profileId));
    }

    @GetMapping("/application/{applicationId}/review-context")
    @SaCheckPermission("profile:enterprise:review")
    public R<ReviewContext> reviewContext(@PathVariable long applicationId) {
        return R.ok(service.review(applicationId));
    }

    @GetMapping("/application/{applicationId}/material/{materialRefId}/access-url")
    @SaCheckPermission("profile:enterprise:review")
    public R<OssService.OssAccessUrl> reviewMaterial(@PathVariable long applicationId,
                                                     @PathVariable long materialRefId) {
        return R.ok(service.reviewMaterial(applicationId, materialRefId));
    }

    @GetMapping("/{profileId}/material/{materialRefId}/access-url")
    @SaCheckPermission("profile:enterprise:material")
    public R<OssService.OssAccessUrl> material(@PathVariable long profileId, @PathVariable long materialRefId) {
        return R.ok(service.material(profileId, materialRefId));
    }

    @PostMapping("/application/{applicationId}/decision")
    @SaCheckPermission("profile:enterprise:override")
    @Log(title = "企业档案管理员决定", businessType = BusinessType.OTHER,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<Result> decide(@PathVariable long applicationId, @RequestBody DecisionCommand command) {
        return R.ok(service.decide(LoginHelper.getUserId(), applicationId, command));
    }

    @PostMapping("/admin-create")
    @SaCheckPermission("profile:enterprise:override")
    @Log(title = "管理员直建企业档案", businessType = BusinessType.INSERT,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<Result> create(@RequestBody CreateCommand command) {
        return R.ok(service.create(LoginHelper.getUserId(), command));
    }

    @PostMapping("/{profileId}/revision")
    @SaCheckPermission("profile:enterprise:override")
    @Log(title = "管理员修订企业档案", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<Result> revise(@PathVariable long profileId, @RequestBody ReviseCommand command) {
        return R.ok(service.revise(LoginHelper.getUserId(), profileId, command));
    }

    @PostMapping("/{profileId}/assign")
    @SaCheckPermission("profile:enterprise:override")
    @Log(title = "管理员指定个人档案账户", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<Result> assign(@PathVariable long profileId, @RequestBody AssignCommand command) {
        return R.ok(service.assign(LoginHelper.getUserId(), profileId, command));
    }

    @PostMapping("/{profileId}/binding")
    @SaCheckPermission("profile:enterprise:manage")
    @Log(title = "企业档案绑定处置", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<Result> manageBinding(@PathVariable long profileId, @RequestBody BindingCommand command) {
        return R.ok(service.manageBinding(LoginHelper.getUserId(), profileId, command));
    }

    @PostMapping("/{profileId}/revoke")
    @SaCheckPermission("profile:enterprise:override")
    @Log(title = "注销企业档案", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<Result> revoke(@PathVariable long profileId, @RequestBody RevokeCommand command) {
        return R.ok(service.revoke(LoginHelper.getUserId(), profileId, command));
    }
}
