package org.dromara.profile.enterprise.transfer;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.profile.enterprise.transfer.EnterpriseTransferContracts.ConfirmCommand;
import org.dromara.profile.enterprise.transfer.EnterpriseTransferContracts.SendCommand;
import org.dromara.profile.enterprise.transfer.EnterpriseTransferContracts.TransferView;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/profile/enterprise/transfer")
public class EnterpriseTransferController {

    private final EnterpriseTransferService service;

    @PostMapping("/send")
    @SaCheckPermission("profile:enterprise:apply")
    @Log(title = "发送企业负责人转移验证码", businessType = BusinessType.OTHER,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<TransferView> send(@Valid @RequestBody SendCommand command) {
        return R.ok(service.send(LoginHelper.getUserId(), command));
    }

    @PostMapping("/confirm")
    @SaCheckPermission("profile:enterprise:apply")
    @Log(title = "确认企业负责人转移", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<TransferView> confirm(@Valid @RequestBody ConfirmCommand command) {
        return R.ok(service.confirm(LoginHelper.getUserId(), command));
    }

    @PostMapping("/unbind")
    @SaCheckPermission("profile:enterprise:apply")
    @Log(title = "企业负责人自行解绑", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<TransferView> unbind() {
        return R.ok(service.unbind(LoginHelper.getUserId()));
    }
}
