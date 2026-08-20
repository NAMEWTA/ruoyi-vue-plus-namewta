package org.dromara.system.api.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.core.constant.RegexConstants;
import org.dromara.common.core.domain.model.LoginBody;
import org.hibernate.validator.constraints.Length;

/**
 * 用户注册对象
 *
 * @author Lion Li
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RegisterBody extends LoginBody {

    /**
     * 注册请求默认授权类型，避免继承 LoginBody 的 grantType 必填约束阻塞公开注册。
     */
    public RegisterBody() {
        setGrantType("password");
    }

    /**
     * 用户名
     */
    @NotBlank(message = "{user.username.not.blank}")
    @Length(min = 2, max = 30, message = "{user.username.length.valid}")
    private String username;

    /**
     * 用户密码
     */
    @NotBlank(message = "{user.password.not.blank}")
    @Length(min = 5, max = 30, message = "{user.password.length.valid}")
//    @Pattern(regexp = RegexConstants.PASSWORD, message = "{user.password.format.valid}")
    private String password;

    /**
     * 可选邮箱。
     */
    @Email(message = "{user.email.not.valid}")
    @Length(max = 50, message = "邮箱长度不能超过50个字符")
    private String email;

    /**
     * 可选手机号码。
     */
    @Pattern(regexp = "^$|" + RegexConstants.MOBILE, message = "{user.mobile.phone.number.not.valid}")
    private String phoneNumber;

}
