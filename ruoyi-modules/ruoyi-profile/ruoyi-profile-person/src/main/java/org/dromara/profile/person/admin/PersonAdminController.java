package org.dromara.profile.person.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.profile.person.admin.PersonAdminContracts.*;
import org.dromara.system.api.OssService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/profile/person/archive")
public class PersonAdminController {

    private final PersonAdminService service;

    @GetMapping
    @SaCheckPermission("profile:person:query")
    public R<PageResult<Summary>> page(Query query) {
        return R.ok(service.page(query));
    }

    @GetMapping("/eligible-users")
    @SaCheckPermission("profile:person:override")
    public R<List<AccountCandidate>> eligibleUsers(@RequestParam String keyword) {
        return R.ok(service.eligibleUsers(keyword));
    }

    @GetMapping("/{profileId}")
    @SaCheckPermission("profile:person:query")
    public R<Detail> detail(@PathVariable long profileId) {
        return R.ok(service.detail(profileId));
    }

    @GetMapping("/application/{applicationId}/review-context")
    @SaCheckPermission("profile:person:review")
    public R<ReviewContext> reviewContext(@PathVariable long applicationId) {
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
    public R<Result> decide(@PathVariable long applicationId, @RequestBody DecisionCommand command) {
        return R.ok(service.decide(LoginHelper.getUserId(), applicationId, command));
    }

    @PostMapping("/admin-create")
    @SaCheckPermission("profile:person:override")
    @Log(title = "管理员直建个人档案", businessType = BusinessType.INSERT,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<Result> create(@RequestBody CreateCommand command) {
        return R.ok(service.create(LoginHelper.getUserId(), command));
    }

    @PostMapping("/{profileId}/revision")
    @SaCheckPermission("profile:person:override")
    @Log(title = "管理员修订个人档案", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<Result> revise(@PathVariable long profileId, @RequestBody ReviseCommand command) {
        return R.ok(service.revise(LoginHelper.getUserId(), profileId, command));
    }

    @PostMapping("/{profileId}/assign")
    @SaCheckPermission("profile:person:override")
    @Log(title = "管理员指定个人档案账户", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<Result> assign(@PathVariable long profileId, @RequestBody AssignCommand command) {
        return R.ok(service.assign(LoginHelper.getUserId(), profileId, command));
    }

    @PostMapping("/{profileId}/binding")
    @SaCheckPermission("profile:person:manage")
    @Log(title = "个人档案绑定处置", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<Result> manageBinding(@PathVariable long profileId, @RequestBody BindingCommand command) {
        return R.ok(service.manageBinding(LoginHelper.getUserId(), profileId, command));
    }

    @PostMapping("/{profileId}/revoke")
    @SaCheckPermission("profile:person:override")
    @Log(title = "注销个人档案", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<Result> revoke(@PathVariable long profileId, @RequestBody RevokeCommand command) {
        return R.ok(service.revoke(LoginHelper.getUserId(), profileId, command));
    }
}
