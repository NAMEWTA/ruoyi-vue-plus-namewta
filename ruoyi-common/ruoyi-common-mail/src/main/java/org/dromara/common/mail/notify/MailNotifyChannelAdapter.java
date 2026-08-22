package org.dromara.common.mail.notify;

import org.dromara.common.notify.exception.NotifyValidationException;
import org.dromara.common.notify.model.*;
import org.dromara.common.notify.spi.NotifyChannelAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * SMTP 邮件通知渠道 Adapter。
 */
public final class MailNotifyChannelAdapter implements NotifyChannelAdapter {

    public static final String CHANNEL = "mail";
    public static final String PROVIDER = "smtp";

    private final MailNotificationSender sender;

    public MailNotifyChannelAdapter(MailNotificationSender sender) {
        this.sender = sender;
    }

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public Set<String> supportedTargetTypes() {
        return Set.of(NotifyTargetType.EMAIL);
    }

    @Override
    public NotifyAdapterResult send(NotifyAdapterRequest adapterRequest) {
        NotifyRequest request = adapterRequest.request();
        if (request.providerKey() != null && !request.providerKey().isBlank()
            && !PROVIDER.equalsIgnoreCase(request.providerKey())) {
            throw new NotifyValidationException("UNKNOWN_PROVIDER", "邮件渠道不支持 Provider: " + request.providerKey());
        }
        MailNotificationMessage message = buildMessage(request);
        long startedAt = System.nanoTime();
        try {
            String messageId = sender.send(message);
            long costTime = elapsedMillis(startedAt);
            List<NotifyTargetResult> results = request.targets().stream()
                .map(target -> NotifyTargetResult.accepted(target, messageId, costTime))
                .toList();
            return new NotifyAdapterResult(PROVIDER, results);
        } catch (RuntimeException exception) {
            long costTime = elapsedMillis(startedAt);
            List<NotifyTargetResult> results = request.targets().stream()
                .map(target -> NotifyTargetResult.failed(target, "PROVIDER_ERROR", "邮件 Provider 调用失败", costTime))
                .toList();
            return new NotifyAdapterResult(PROVIDER, results);
        }
    }

    private MailNotificationMessage buildMessage(NotifyRequest request) {
        List<String> to = new ArrayList<>();
        List<String> cc = new ArrayList<>();
        List<String> bcc = new ArrayList<>();
        for (NotifyTarget target : request.targets()) {
            String role = target.role();
            if (role == null || NotifyTargetRole.DIRECT.equalsIgnoreCase(role)
                || NotifyTargetRole.TO.equalsIgnoreCase(role)) {
                to.add(target.value());
            } else if (NotifyTargetRole.CC.equalsIgnoreCase(role)) {
                cc.add(target.value());
            } else if (NotifyTargetRole.BCC.equalsIgnoreCase(role)) {
                bcc.add(target.value());
            } else {
                throw new NotifyValidationException("INVALID_TARGET_ROLE", "邮件目标角色无效: " + role);
            }
        }
        if (to.isEmpty()) {
            throw new NotifyValidationException("MAIL_TO_REQUIRED", "邮件至少需要一个 TO 收件人");
        }
        NotifyContent content = request.content();
        boolean html = content instanceof NotifyRichContent rich && rich.html();
        return new MailNotificationMessage(to, cc, bcc, content.subject(), content.contentSnapshot(), html);
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
}
