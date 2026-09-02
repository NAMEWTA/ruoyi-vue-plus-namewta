package org.dromara.profile.person.controller.admin;

import org.dromara.profile.person.domain.exception.PersonAdminException;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = PersonAdminController.class)
public class PersonAdminExceptionHandler {

    @ExceptionHandler(PersonAdminException.class)
    public R<Void> handle(PersonAdminException exception) {
        return R.fail(exception.getMessage());
    }
}
