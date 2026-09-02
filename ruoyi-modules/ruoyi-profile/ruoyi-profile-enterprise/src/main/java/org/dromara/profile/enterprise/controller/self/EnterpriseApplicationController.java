package org.dromara.profile.enterprise.controller.self;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.profile.enterprise.domain.bo.EnterpriseApplicationProbeBo;
import org.dromara.profile.enterprise.domain.bo.EnterpriseApplicationSaveBo;
import org.dromara.profile.enterprise.domain.bo.EnterpriseApplicationSubmitBo;
import org.dromara.profile.enterprise.domain.vo.EnterpriseApplicationProbeVo;
import org.dromara.profile.enterprise.domain.vo.EnterpriseApplicationVo;
import org.dromara.profile.enterprise.service.IEnterpriseApplicationService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/profile/enterprise/application")
public class EnterpriseApplicationController {

    private final IEnterpriseApplicationService service;

    @GetMapping
    @SaCheckPermission("profile:enterprise:apply")
    public R<EnterpriseApplicationVo> current() {
        return R.ok(service.current(LoginHelper.getUserId()).orElse(null));
    }

    @PostMapping
    @SaCheckPermission("profile:enterprise:apply")
    @Log(title = "保存企业实名认证申请", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<EnterpriseApplicationVo> save(@Valid @RequestBody EnterpriseApplicationSaveBo command) {
        return R.ok(service.save(LoginHelper.getUserId(), command));
    }

    @PostMapping("/submit")
    @SaCheckPermission("profile:enterprise:apply")
    @Log(title = "提交企业实名认证申请", businessType = BusinessType.OTHER,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<EnterpriseApplicationVo> submit(@Valid @RequestBody EnterpriseApplicationSubmitBo command) {
        return R.ok(service.submit(LoginHelper.getUserId(), command.expectedVersion()));
    }

    @PostMapping("/probe")
    @SaCheckPermission("profile:enterprise:apply")
    @Log(title = "探测企业认证状态", businessType = BusinessType.OTHER,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<EnterpriseApplicationProbeVo> probe(@Valid @RequestBody EnterpriseApplicationProbeBo command) {
        return R.ok(service.probe(command));
    }
}
