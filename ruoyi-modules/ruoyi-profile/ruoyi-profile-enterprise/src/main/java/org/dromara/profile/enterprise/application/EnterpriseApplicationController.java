package org.dromara.profile.enterprise.application;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/profile/enterprise/application")
public class EnterpriseApplicationController {

    private final EnterpriseApplicationService service;

    @GetMapping
    @SaCheckPermission("profile:enterprise:apply")
    public R<EnterpriseApplicationView> current() {
        return R.ok(service.current(LoginHelper.getUserId()).orElse(null));
    }

    @PostMapping
    @SaCheckPermission("profile:enterprise:apply")
    @Log(title = "保存企业实名认证申请", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<EnterpriseApplicationView> save(@RequestBody EnterpriseDraftCommand command) {
        return R.ok(service.save(LoginHelper.getUserId(), command));
    }

    @PostMapping("/submit")
    @SaCheckPermission("profile:enterprise:apply")
    @Log(title = "提交企业实名认证申请", businessType = BusinessType.OTHER,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<EnterpriseApplicationView> submit(@RequestBody EnterpriseSubmitCommand command) {
        return R.ok(service.submit(LoginHelper.getUserId(), command.expectedVersion()));
    }

    @PostMapping("/probe")
    @SaCheckPermission("profile:enterprise:apply")
    @Log(title = "探测企业认证状态", businessType = BusinessType.OTHER,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<EnterpriseProbeView> probe(@RequestBody EnterpriseProbeCommand command) {
        return R.ok(service.probe(command));
    }
}
