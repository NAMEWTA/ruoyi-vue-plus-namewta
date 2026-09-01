package org.dromara.profile.enterprise.transfer;

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
