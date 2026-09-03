package org.dromara.profile.person.controller.self;

import jakarta.validation.Valid;

import org.dromara.profile.person.usecase.PersonRebindUseCase;
import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.profile.person.domain.bo.PersonRebindConfirmBo;
import org.dromara.profile.person.domain.vo.PersonRebindConfirmationVo;
import org.dromara.profile.person.domain.bo.PersonRebindMatchBo;
import org.dromara.profile.person.domain.vo.PersonRebindMatchVo;
import org.dromara.profile.person.domain.bo.PersonRebindProbeBo;
import org.dromara.profile.person.domain.vo.PersonRebindProbeVo;
import org.dromara.profile.person.domain.vo.PersonRebindSubmissionVo;
import org.dromara.profile.person.domain.bo.PersonRebindSubmitBo;
import org.dromara.profile.person.domain.vo.PersonRebindUnbindVo;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PersonRebindController HTTP 接口，负责参数校验和响应包装。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/profile/person/rebind")
public class PersonRebindController {

    private final PersonRebindUseCase service;

    /**
     * 处理 probe HTTP 请求。
     */
    @PostMapping("/probe")
    @SaCheckPermission("profile:person:apply")
    @Log(title = "探测个人认证绑定状态", businessType = BusinessType.OTHER,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<PersonRebindProbeVo> probe(@Valid @RequestBody PersonRebindProbeBo command) {
        return R.ok(service.probe(command));
    }

    /**
     * 处理 match HTTP 请求。
     */
    @PostMapping("/match")
    @SaCheckPermission("profile:person:apply")
    @Log(title = "核验个人换绑身份", businessType = BusinessType.OTHER,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<PersonRebindMatchVo> match(@Valid @RequestBody PersonRebindMatchBo command) {
        return R.ok(service.match(LoginHelper.getUserId(), command));
    }

    /**
     * 处理 confirm HTTP 请求。
     */
    @PostMapping("/confirm")
    @SaCheckPermission("profile:person:apply")
    @Log(title = "确认个人换绑意图", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<PersonRebindConfirmationVo> confirm(@Valid @RequestBody PersonRebindConfirmBo command) {
        return R.ok(service.confirm(LoginHelper.getUserId(), command));
    }

    /**
     * 处理 submit HTTP 请求。
     */
    @PostMapping("/submit")
    @SaCheckPermission("profile:person:apply")
    @Log(title = "提交个人换绑申请", businessType = BusinessType.OTHER,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<PersonRebindSubmissionVo> submit(@Valid @RequestBody PersonRebindSubmitBo command) {
        return R.ok(service.submit(LoginHelper.getUserId(), command));
    }

    /**
     * 处理 unbind HTTP 请求。
     */
    @PostMapping("/unbind")
    @SaCheckPermission("profile:person:apply")
    @Log(title = "解绑个人实名认证", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<PersonRebindUnbindVo> unbind() {
        return R.ok(service.unbind(LoginHelper.getUserId()));
    }
}
