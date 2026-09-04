package org.dromara.third.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.third.domain.bo.ThirdCredentialBo;
import org.dromara.third.service.ThirdCredentialUseCase;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/third/credential")
public class ThirdCredentialController {
    private final ThirdCredentialUseCase credentialUseCase;

    @GetMapping("/list")
    @SaCheckPermission("third:credential:list")
    public R<?> list(@RequestParam String providerCode, @RequestParam(required = false) String endpointCode) {
        return R.ok(credentialUseCase.list(providerCode, endpointCode));
    }

    @PostMapping
    @Log(title = "第三方凭据", businessType = BusinessType.INSERT)
    @SaCheckPermission("third:credential:add")
    public R<Void> save(@Valid @RequestBody ThirdCredentialBo bo) {
        credentialUseCase.save(bo);
        return R.ok();
    }

    @DeleteMapping("/{credentialId}")
    @Log(title = "第三方凭据", businessType = BusinessType.DELETE)
    @SaCheckPermission("third:credential:remove")
    public R<Void> remove(@PathVariable Long credentialId) {
        credentialUseCase.remove(credentialId);
        return R.ok();
    }
}
