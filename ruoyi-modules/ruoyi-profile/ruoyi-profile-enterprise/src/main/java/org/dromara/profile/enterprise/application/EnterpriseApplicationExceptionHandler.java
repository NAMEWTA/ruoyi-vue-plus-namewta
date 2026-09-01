package org.dromara.profile.enterprise.application;

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
