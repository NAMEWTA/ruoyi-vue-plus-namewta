package org.dromara.demo.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.notify.api.NotificationApplicationService;
import org.dromara.notify.api.NotificationChannel;
import org.dromara.notify.api.NotificationCommand;
import org.dromara.notify.api.NotificationMode;
import org.dromara.notify.api.NotificationStrategy;
import org.dromara.sms4j.api.SmsBlend;
import org.dromara.sms4j.core.factory.SmsFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 短信演示案例
 * 请先阅读文档 否则无法使用
 *
 * @author Lion Li
 * @version 4.2.0
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/demo/sms")
public class SmsController {

    private final NotificationApplicationService notificationService;
    /**
     * 发送短信Aliyun
     *
     * @param phones     电话号
     * @param templateId 模板ID
     */
    @GetMapping("/sendAliyun")
    public R<Object> sendAliyun(String phones, String templateId) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>(1);
        map.put("code", "1234");
        return sendTemplate(phones, templateId, "config1", map, "短信验证码：1234");
    }

    /**
     * 发送短信Tencent
     *
     * @param phones     电话号
     * @param templateId 模板ID
     */
    @GetMapping("/sendTencent")
    public R<Object> sendTencent(String phones, String templateId) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>(1);
//        map.put("2", "测试测试");
        map.put("1", "1234");
        return sendTemplate(phones, templateId, "config2", map, "短信验证码：1234");
    }

    /**
     * 添加黑名单
     *
     * @param phone 手机号
     */
    @GetMapping("/addBlacklist")
    public R<Object> addBlacklist(String phone) {
        SmsBlend smsBlend = SmsFactory.getSmsBlend("config1");
        smsBlend.joinInBlacklist(phone);
        return R.ok();
    }

    /**
     * 移除黑名单
     *
     * @param phone 手机号
     */
    @GetMapping("/removeBlacklist")
    public R<Object> removeBlacklist(String phone) {
        SmsBlend smsBlend = SmsFactory.getSmsBlend("config1");
        smsBlend.removeFromBlacklist(phone);
        return R.ok();
    }

    private R<Object> sendTemplate(String phones, String templateId, String providerKey,
                                   LinkedHashMap<String, String> params, String contentSnapshot) {
        List<String> targets = Arrays.stream(phones.split(","))
            .map(String::trim)
            .filter(phone -> !phone.isEmpty())
            .toList();
        return R.ok(notificationService.submit(new NotificationCommand("demo", "sms-demo", "demo_sms",
            String.join(",", targets), "PHONE", targets, templateId,
            Map.of("content", contentSnapshot, "providerKey", providerKey, "params", params),
            List.of(NotificationChannel.SMS), NotificationStrategy.ALL, NotificationMode.SYNC, 20,
            null, null, null, Map.of())));
    }

}
