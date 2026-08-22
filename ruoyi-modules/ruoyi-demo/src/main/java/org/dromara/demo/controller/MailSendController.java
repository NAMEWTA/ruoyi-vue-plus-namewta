package org.dromara.demo.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.notify.core.NotifyClient;
import org.dromara.common.notify.model.NotifyRequest;
import org.dromara.common.notify.model.NotifyTarget;
import org.dromara.common.notify.model.NotifyTextContent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


/**
 * 邮件发送案例
 *
 * @author Michelle.Chung
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/demo/mail")
public class MailSendController {

    private final NotifyClient notifyClient;

    /**
     * 发送邮件
     *
     * @param to      接收人
     * @param subject 标题
     * @param text    内容
     */
    @GetMapping("/sendSimpleMessage")
    public R<Void> sendSimpleMessage(String to, String subject, String text) {
        send(to, subject, text, List.of());
        return R.ok();
    }

    /**
     * 发送邮件（带附件）
     *
     * @param to      接收人
     * @param subject 标题
     * @param text    内容
     */
    @GetMapping("/sendMessageWithAttachment")
    public R<Void> sendMessageWithAttachment(String to, String subject, String text, Long ossId) {
        send(to, subject, text, List.of(ossId));
        return R.ok();
    }

    /**
     * 发送邮件（多附件）
     *
     * @param to      接收人
     * @param subject 标题
     * @param text    内容
     */
    @GetMapping("/sendMessageWithAttachments")
    public R<Void> sendMessageWithAttachments(String to, String subject, String text, List<Long> ossIds) {
        send(to, subject, text, ossIds);
        return R.ok();
    }

    private void send(String to, String subject, String text, List<Long> ossIds) {
        notifyClient.send(NotifyRequest.builder()
            .bizType("demo_mail")
            .channel("mail")
            .targets(List.of(NotifyTarget.email(to)))
            .content(new NotifyTextContent(subject, text))
            .attachmentOssIds(ossIds)
            .build());
    }

}
