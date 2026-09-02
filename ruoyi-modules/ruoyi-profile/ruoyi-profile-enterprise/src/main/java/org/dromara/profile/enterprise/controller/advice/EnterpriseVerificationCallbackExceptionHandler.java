package org.dromara.profile.enterprise.controller.advice;

import org.dromara.profile.enterprise.controller.anonymous.EnterpriseVerificationAnonymousController;
import org.dromara.profile.enterprise.domain.exception.EnterpriseVerificationException;
import org.dromara.common.core.domain.R;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = EnterpriseVerificationAnonymousController.class)
public class EnterpriseVerificationCallbackExceptionHandler {

    @ExceptionHandler(EnterpriseVerificationException.class)
    public ResponseEntity<R<FailureResponse>> handle(EnterpriseVerificationException failure) {
        HttpStatus status = switch (failure.category()) {
            case INVALID_SIGNATURE, EXPIRED_CALLBACK -> HttpStatus.UNAUTHORIZED;
            default -> HttpStatus.BAD_REQUEST;
        };
        FailureResponse data = new FailureResponse(failure.category().name());
        return ResponseEntity.status(status)
            .body(R.fail("Enterprise verification callback rejected", data));
    }

    public record FailureResponse(String category) {
    }
}
