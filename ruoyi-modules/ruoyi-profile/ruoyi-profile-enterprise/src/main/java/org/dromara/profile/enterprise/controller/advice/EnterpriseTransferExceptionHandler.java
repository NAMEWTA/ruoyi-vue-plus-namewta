package org.dromara.profile.enterprise.controller.advice;

import org.dromara.profile.enterprise.controller.self.EnterpriseTransferController;
import org.dromara.profile.enterprise.domain.exception.EnterpriseTransferException;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = EnterpriseTransferController.class)
public class EnterpriseTransferExceptionHandler {

    @ExceptionHandler(EnterpriseTransferException.class)
    public R<Void> handle(EnterpriseTransferException exception) {
        return R.fail(exception.getMessage());
    }
}
