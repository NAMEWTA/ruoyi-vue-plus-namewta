package org.dromara.profile.person.verification;

import org.dromara.common.core.domain.R;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PersonVerificationCallbackController.class)
public class PersonVerificationCallbackExceptionHandler {

    @ExceptionHandler(PersonVerificationException.class)
    public ResponseEntity<R<FailureResponse>> handle(PersonVerificationException failure) {
        HttpStatus status = switch (failure.category()) {
            case INVALID_SIGNATURE, EXPIRED_CALLBACK -> HttpStatus.UNAUTHORIZED;
            default -> HttpStatus.BAD_REQUEST;
        };
        FailureResponse data = new FailureResponse(failure.category().name());
        return ResponseEntity.status(status)
            .body(R.fail("Person verification callback rejected", data));
    }

    public record FailureResponse(String category) {
    }
}
