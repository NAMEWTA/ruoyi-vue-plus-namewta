package org.dromara.profile.person.controller.self;

import jakarta.validation.Valid;

import org.dromara.profile.person.domain.vo.PersonApplicationVo;
import org.dromara.profile.person.domain.bo.PersonApplicationSaveBo;
import org.dromara.profile.person.domain.bo.PersonApplicationSubmitBo;
import org.dromara.profile.person.service.IPersonApplicationService;
import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/profile/person/application")
public class PersonApplicationController {

    private final IPersonApplicationService service;

    @GetMapping
    @SaCheckPermission("profile:person:apply")
    public R<PersonApplicationVo> current() {
        return R.ok(service.current(LoginHelper.getUserId()).orElse(null));
    }

    @PostMapping
    @SaCheckPermission("profile:person:apply")
    @Log(title = "保存个人实名认证申请", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<PersonApplicationVo> save(@Valid @RequestBody PersonApplicationSaveBo command) {
        return R.ok(service.save(LoginHelper.getUserId(), command));
    }

    @PostMapping("/submit")
    @SaCheckPermission("profile:person:apply")
    @Log(title = "提交个人实名认证申请", businessType = BusinessType.OTHER,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<PersonApplicationVo> submit(@Valid @RequestBody PersonApplicationSubmitBo command) {
        return R.ok(service.submit(LoginHelper.getUserId(), command.expectedVersion()));
    }
}
