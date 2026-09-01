package org.dromara.profile.person.verification;

import cn.dev33.satoken.annotation.SaIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/profile/person/verification/providers")
public class PersonVerificationCallbackController {

    private final PersonVerificationAttemptCoordinator coordinator;
    private final PersonVerificationTimeSource timeSource;

    public PersonVerificationCallbackController(PersonVerificationAttemptCoordinator coordinator,
                                                 PersonVerificationTimeSource timeSource) {
        this.coordinator = coordinator;
        this.timeSource = timeSource;
    }

    @SaIgnore
    @Log(title = "个人认证供应商回调", businessType = BusinessType.OTHER,
        isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/{providerCode}/callback")
    public R<PersonVerificationCallbackOutcome> callback(
        @PathVariable String providerCode,
        @Valid @RequestBody CallbackRequest request) {
        PersonVerificationCallbackOutcome outcome = coordinator.handleCallback(
            providerCode,
            new PersonProviderCallbackEnvelope(
                request.providerRequestId(),
                request.timestampEpochSecond(),
                request.payload(),
                request.signature()),
            timeSource.now());
        return R.ok(outcome);
    }

    public record CallbackRequest(
        @NotBlank @Size(max = 128) String providerRequestId,
        @Positive long timestampEpochSecond,
        @NotBlank @Size(max = 1048576) String payload,
        @NotBlank @Size(max = 1024) String signature
    ) {
    }
}
