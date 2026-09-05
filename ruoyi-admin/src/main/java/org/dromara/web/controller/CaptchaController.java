package org.dromara.web.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.hutool.captcha.generator.CodeGenerator;
import cn.hutool.captcha.generator.MathGenerator;
import cn.hutool.captcha.generator.RandomGenerator;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.constant.Constants;
import org.dromara.common.core.constant.GlobalConstants;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.SpringUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.core.utils.regex.RegexValidator;
import org.dromara.common.mail.config.properties.MailProperties;
import org.dromara.notify.api.NotificationApplicationService;
import org.dromara.notify.api.NotificationChannel;
import org.dromara.notify.api.NotificationCommand;
import org.dromara.notify.api.NotificationMode;
import org.dromara.notify.api.NotificationStrategy;
import org.dromara.common.redis.annotation.RateLimiter;
import org.dromara.common.redis.enums.LimitType;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.common.web.config.properties.CaptchaProperties;
import org.dromara.common.web.core.WaveAndCircleCaptcha;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.awt.*;
import java.time.Duration;
import java.util.Map;
import java.util.List;

/**
 * 验证码操作处理
 *
 * @author Lion Li
 */
@SaIgnore
@Slf4j
@Validated
@RequiredArgsConstructor
@RestController
public class CaptchaController {

    private final CaptchaProperties captchaProperties;
    private final MailProperties mailProperties;
    private final NotificationApplicationService notificationService;

    /**
     * 发送短信验证码。
     *
     * @param phoneNumber 用户手机号
     * @return 操作结果
     */
    @RateLimiter(key = "#phoneNumber", time = 60, count = 1)
    @GetMapping("/resource/sms/code")
    public R<Void> smsCode(@NotBlank(message = "{user.phonenumber.not.blank}") String phoneNumber) {
        if (!RegexValidator.isMobile(phoneNumber)) {
            return R.fail("请输入正确的手机号！");
        }
        String key = GlobalConstants.CAPTCHA_CODE_KEY + phoneNumber;
        String code = RandomUtil.randomNumbers(4);
        // 验证码模板id 自行处理 (查数据库或写死均可)
        String templateId = "";
        String content = "您本次验证码为：" + code + "，有效性为" + Constants.CAPTCHA_EXPIRATION + "分钟，请尽快填写。";
        try {
            notificationService.submit(new NotificationCommand("admin-web", "auth-captcha", "auth_captcha", phoneNumber,
                "PHONE", List.of(phoneNumber), templateId, Map.of("code", code, "title", "登录验证码", "content", content),
                List.of(NotificationChannel.SMS), NotificationStrategy.ALL, NotificationMode.SYNC, 80, null, null,
                captchaIdempotencyKey(NotificationChannel.SMS, phoneNumber), Map.of("audit", "REDACT_SENSITIVE")));
        } catch (Exception ex) {
            log.error("验证码短信发送失败，异常类型={}", ex.getClass().getSimpleName());
            return R.fail("验证码短信发送失败");
        }
        cacheCaptchaCode(key, code);
        return R.ok();
    }

    /**
     * 发送邮箱验证码
     *
     * @param email 邮箱
     * @return 操作结果
     */
    @GetMapping("/resource/email/code")
    public R<Void> emailCode(@NotBlank(message = "{user.email.not.blank}") String email) {
        if (!mailProperties.getEnabled()) {
            return R.fail("当前系统没有开启邮箱功能！");
        }
        if (!RegexValidator.isEmail(email)) {
            return R.fail("请输入正确的邮箱地址！");
        }
        SpringUtils.getAopProxy(this).emailCodeImpl(email);
        return R.ok();
    }

    /**
     * 发送邮箱验证码的实际执行方法，拆分出来避免开关关闭时仍触发限流。
     *
     * @param email 邮箱
     */
    @RateLimiter(key = "#email", time = 60, count = 1)
    public void emailCodeImpl(String email) {
        String key = GlobalConstants.CAPTCHA_CODE_KEY + email;
        String code = RandomUtil.randomNumbers(4);
        String content = "您本次验证码为：" + code + "，有效性为" + Constants.CAPTCHA_EXPIRATION + "分钟，请尽快填写。";
        try {
            notificationService.submit(new NotificationCommand("admin-web", "auth-captcha", "auth_captcha", email,
                "EMAIL", List.of(email), "", Map.of("title", "登录验证码", "content", content),
                List.of(NotificationChannel.MAIL), NotificationStrategy.ALL, NotificationMode.SYNC, 80, null, null,
                captchaIdempotencyKey(NotificationChannel.MAIL, email), Map.of("audit", "REDACT_SENSITIVE")));
            cacheCaptchaCode(key, code);
        } catch (Exception e) {
            log.error("验证码邮件发送失败，异常类型={}", e.getClass().getSimpleName());
            throw new ServiceException("验证码邮件发送失败");
        }
    }

    private String captchaIdempotencyKey(NotificationChannel channel, String target) {
        long minute = System.currentTimeMillis() / Duration.ofMinutes(1).toMillis();
        return "captcha:" + channel.name().toLowerCase() + ":" + target + ":" + minute;
    }

    protected void cacheCaptchaCode(String key, String code) {
        RedisUtils.setCacheObject(key, code, Duration.ofMinutes(Constants.CAPTCHA_EXPIRATION));
    }

    /**
     * 获取图片验证码。
     *
     * @return 验证码信息
     */
    @GetMapping("/auth/code")
    public R<CaptchaVo> getCode() {
        CaptchaProperties.Snapshot settings = captchaProperties.currentSnapshot();
        if (!Boolean.TRUE.equals(settings.enable())) {
            return R.ok(new CaptchaVo(false, null, null));
        }
        return R.ok(SpringUtils.getAopProxy(this).getCodeImpl(settings));
    }

    /**
     * 实际生成图片验证码并缓存结果。
     *
     * @return 验证码信息
     */
    @RateLimiter(time = 60, count = 10, limitType = LimitType.IP)
    public CaptchaVo getCodeImpl() {
        return getCodeImpl(captchaProperties.currentSnapshot());
    }

    /**
     * 使用请求开始时捕获的完整快照生成验证码。
     */
    @RateLimiter(time = 60, count = 10, limitType = LimitType.IP)
    public CaptchaVo getCodeImpl(CaptchaProperties.Snapshot settings) {
        // 保存验证码信息
        String uuid = IdUtil.simpleUUID();
        String verifyKey = GlobalConstants.CAPTCHA_CODE_KEY + uuid;
        // 生成验证码
        String captchaType = settings.type();
        CodeGenerator codeGenerator;
        if ("math".equals(captchaType)) {
            codeGenerator = new MathGenerator(settings.numberLength(), false);
        } else {
            codeGenerator = new RandomGenerator(settings.charLength());
        }
        WaveAndCircleCaptcha captcha = new WaveAndCircleCaptcha(160, 60);
        // captcha.setBackground(Color.WHITE); // 不设置就是透明底
        captcha.setFont(new Font("Arial", Font.BOLD, 45));
        captcha.setGenerator(codeGenerator);
        captcha.createCode();
        // 如果是数学验证码，使用SpEL表达式处理验证码结果
        String code = captcha.getCode();
        if ("math".equals(captchaType)) {
            ExpressionParser parser = new SpelExpressionParser();
            Expression exp = parser.parseExpression(StringUtils.remove(code, "="));
            code = exp.getValue(String.class);
        }
        RedisUtils.setCacheObject(verifyKey, code, Duration.ofMinutes(Constants.CAPTCHA_EXPIRATION));
        return new CaptchaVo(true, uuid, captcha.getImageBase64());
    }

    /**
     * 图片验证码响应对象。
     *
     * @param captchaEnabled 是否启用验证码
     * @param uuid           验证码标识
     * @param img            Base64 图片数据
     */
    public record CaptchaVo(Boolean captchaEnabled, String uuid, String img) {
    }

}
