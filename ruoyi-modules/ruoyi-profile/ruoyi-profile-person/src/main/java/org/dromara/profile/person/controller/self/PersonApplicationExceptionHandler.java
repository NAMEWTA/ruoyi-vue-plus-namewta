package org.dromara.profile.person.controller.self;

import org.dromara.profile.person.domain.exception.PersonApplicationException;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = PersonApplicationController.class)
public class PersonApplicationExceptionHandler {

    @ExceptionHandler(PersonApplicationException.class)
    public R<Void> handle(PersonApplicationException exception) {
        return R.fail(exception.getMessage());
    }
}
