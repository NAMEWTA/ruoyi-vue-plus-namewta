package org.dromara.profile.person.rebind;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.profile.person.rebind.PersonRebindContracts.ConfirmCommand;
import org.dromara.profile.person.rebind.PersonRebindContracts.ConfirmationView;
import org.dromara.profile.person.rebind.PersonRebindContracts.MatchCommand;
import org.dromara.profile.person.rebind.PersonRebindContracts.MatchView;
import org.dromara.profile.person.rebind.PersonRebindContracts.ProbeCommand;
import org.dromara.profile.person.rebind.PersonRebindContracts.ProbeView;
import org.dromara.profile.person.rebind.PersonRebindContracts.SubmissionView;
import org.dromara.profile.person.rebind.PersonRebindContracts.SubmitCommand;
import org.dromara.profile.person.rebind.PersonRebindContracts.UnbindView;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/profile/person/rebind")
public class PersonRebindController {

    private final PersonRebindService service;

    @PostMapping("/probe")
    @SaCheckPermission("profile:person:apply")
    public R<ProbeView> probe(@RequestBody ProbeCommand command) {
        return R.ok(service.probe(command));
    }

    @PostMapping("/match")
    @SaCheckPermission("profile:person:apply")
    @Log(title = "核验个人换绑身份", businessType = BusinessType.OTHER,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<MatchView> match(@RequestBody MatchCommand command) {
        return R.ok(service.match(LoginHelper.getUserId(), command));
    }

    @PostMapping("/confirm")
    @SaCheckPermission("profile:person:apply")
    @Log(title = "确认个人换绑意图", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<ConfirmationView> confirm(@RequestBody ConfirmCommand command) {
        return R.ok(service.confirm(LoginHelper.getUserId(), command));
    }

    @PostMapping("/submit")
    @SaCheckPermission("profile:person:apply")
    @Log(title = "提交个人换绑申请", businessType = BusinessType.OTHER,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<SubmissionView> submit(@RequestBody SubmitCommand command) {
        return R.ok(service.submit(LoginHelper.getUserId(), command));
    }

    @PostMapping("/unbind")
    @SaCheckPermission("profile:person:apply")
    @Log(title = "解绑个人实名认证", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<UnbindView> unbind() {
        return R.ok(service.unbind(LoginHelper.getUserId()));
    }
}
