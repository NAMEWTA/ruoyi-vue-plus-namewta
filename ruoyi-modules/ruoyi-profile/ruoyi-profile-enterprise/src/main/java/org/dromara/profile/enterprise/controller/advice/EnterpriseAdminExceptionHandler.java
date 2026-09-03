package org.dromara.profile.enterprise.controller.advice;

import org.dromara.profile.enterprise.controller.admin.EnterpriseAdminController;
import org.dromara.profile.enterprise.domain.exception.EnterpriseAdminException;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** EnterpriseAdminExceptionHandler 控制器，提供本能力的 HTTP 接口。 */
@RestControllerAdvice(basePackageClasses = EnterpriseAdminController.class)
public class EnterpriseAdminExceptionHandler {

    /** 处理业务异常并返回统一响应。 */
    @ExceptionHandler(EnterpriseAdminException.class)
    public R<Void> handle(EnterpriseAdminException exception) {
        return R.fail(exception.getMessage());
    }
}
