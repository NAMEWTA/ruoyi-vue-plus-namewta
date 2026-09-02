package org.dromara.profile.enterprise.controller.advice;

import org.dromara.profile.enterprise.controller.self.EnterpriseApplicationController;
import org.dromara.profile.enterprise.domain.exception.EnterpriseApplicationException;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = EnterpriseApplicationController.class)
public class EnterpriseApplicationExceptionHandler {

    @ExceptionHandler(EnterpriseApplicationException.class)
    public R<Void> handle(EnterpriseApplicationException exception) {
        return R.fail(exception.getMessage());
    }
}
