package org.dromara.profile.person.controller.self;

import org.dromara.profile.person.domain.exception.PersonRebindException;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = PersonRebindController.class)
public class PersonRebindExceptionHandler {

    @ExceptionHandler(PersonRebindException.class)
    public R<Void> handle(PersonRebindException exception) {
        return R.fail(exception.getMessage());
    }
}
