package org.dromara.profile.shared.material;

import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = {
    "org.dromara.profile.shared.material",
    "org.dromara.profile.enterprise.material"
})
public class ProfileMaterialExceptionHandler {

    @ExceptionHandler(ProfileMaterialException.class)
    public R<Void> handle(ProfileMaterialException exception) {
        return R.fail(exception.getMessage());
    }
}
