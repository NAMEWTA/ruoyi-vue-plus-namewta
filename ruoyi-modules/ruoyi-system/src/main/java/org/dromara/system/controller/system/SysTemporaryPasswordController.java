package org.dromara.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.system.domain.bo.password.TemporaryPasswordIssueBo;
import org.dromara.system.domain.vo.password.TemporaryPasswordVo;
import org.dromara.system.service.ISysUserService;
import org.dromara.system.temporarypassword.TemporaryPasswordAuditEnricher;
import org.dromara.system.temporarypassword.TemporaryPasswordService;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户临时密码签发接口。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/user")
public class SysTemporaryPasswordController {

    private final ISysUserService userService;
    private final TemporaryPasswordService temporaryPasswordService;
    private final TemporaryPasswordAuditEnricher auditEnricher;

    /**
     * 签发 60 秒有效、单次消费的临时密码，不修改永久密码。
     */
    @SaCheckPermission("system:user:temporaryPassword")
    @Log(title = TemporaryPasswordAuditEnricher.AUDIT_TITLE, businessType = BusinessType.OTHER,
        isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/temporaryPassword")
    public R<TemporaryPasswordVo> issue(@Validated @RequestBody TemporaryPasswordIssueBo body,
                                         HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        auditEnricher.attachTarget(body.userId());
        userService.checkUserAllowed(body.userId());
        userService.checkUserDataScope(body.userId());
        TemporaryPasswordService.IssuedPassword issued = temporaryPasswordService.issue(body.userId());
        return R.ok(new TemporaryPasswordVo(issued.password(), issued.expiresInSeconds()));
    }
}
