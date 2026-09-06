package org.dromara.system.api.model;

import jakarta.validation.constraints.NotBlank;
import org.dromara.common.core.validation.ValidFormat;
import org.dromara.common.core.validation.ValidationFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.core.domain.model.LoginBody;

/**
 * 邮箱验证码登录请求对象。
 *
 * @author Lion Li
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EmailLoginBody extends LoginBody {

    /**
     * 邮箱
     */
    @NotBlank(message = "{user.email.not.blank}")
    @ValidFormat(type = ValidationFormat.EMAIL, message = "{user.email.not.valid}")
    private String email;

    /**
     * 邮箱code
     */
    @NotBlank(message = "{email.code.not.blank}")
    private String emailCode;

}
