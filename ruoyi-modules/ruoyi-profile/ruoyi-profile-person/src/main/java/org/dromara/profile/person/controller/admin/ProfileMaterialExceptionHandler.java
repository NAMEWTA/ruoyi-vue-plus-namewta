package org.dromara.profile.person.controller.admin;

import org.dromara.profile.person.domain.exception.ProfileMaterialException;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = {
    "org.dromara.profile.person.controller.admin",
    "org.dromara.profile.person.controller.self",
    "org.dromara.profile.enterprise.controller.admin",
    "org.dromara.profile.enterprise.controller.self"
})
public class ProfileMaterialExceptionHandler {

    @ExceptionHandler(ProfileMaterialException.class)
    public R<Void> handle(ProfileMaterialException exception) {
        return R.fail(exception.getMessage());
    }
}
