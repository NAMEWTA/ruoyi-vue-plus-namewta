package org.dromara.profile.enterprise.controller.self;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.profile.enterprise.domain.bo.EnterpriseTransferConfirmBo;
import org.dromara.profile.enterprise.domain.bo.EnterpriseTransferSendBo;
import org.dromara.profile.enterprise.domain.vo.EnterpriseTransferVo;
import org.dromara.profile.enterprise.usecase.EnterpriseTransferUseCase;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * EnterpriseTransferController HTTP 接口，负责参数校验和响应包装。
 */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/profile/enterprise/transfer")
public class EnterpriseTransferController {

    private final EnterpriseTransferUseCase service;

    /**
     * 处理 send HTTP 请求。
     */
    @PostMapping("/send")
    @SaCheckPermission("profile:enterprise:apply")
    @Log(title = "发送企业负责人转移验证码", businessType = BusinessType.OTHER,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<EnterpriseTransferVo> send(@Valid @RequestBody EnterpriseTransferSendBo command) {
        return R.ok(service.send(command));
    }

    /**
     * 处理 confirm HTTP 请求。
     */
    @PostMapping("/confirm")
    @SaCheckPermission("profile:enterprise:apply")
    @Log(title = "确认企业负责人转移", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<EnterpriseTransferVo> confirm(@Valid @RequestBody EnterpriseTransferConfirmBo command) {
        return R.ok(service.confirm(command));
    }

    /**
     * 处理 unbind HTTP 请求。
     */
    @PostMapping("/unbind")
    @SaCheckPermission("profile:enterprise:apply")
    @Log(title = "企业负责人自行解绑", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<EnterpriseTransferVo> unbind() {
        return R.ok(service.unbind());
    }
}
