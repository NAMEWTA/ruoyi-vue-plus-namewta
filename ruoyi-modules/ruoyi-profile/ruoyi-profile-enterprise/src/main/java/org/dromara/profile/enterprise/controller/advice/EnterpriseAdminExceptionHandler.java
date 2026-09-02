package org.dromara.profile.enterprise.controller.advice;

import org.dromara.profile.enterprise.controller.admin.EnterpriseAdminController;
import org.dromara.profile.enterprise.domain.exception.EnterpriseAdminException;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = EnterpriseAdminController.class)
public class EnterpriseAdminExceptionHandler {

    @ExceptionHandler(EnterpriseAdminException.class)
    public R<Void> handle(EnterpriseAdminException exception) {
        return R.fail(exception.getMessage());
    }
}
