package org.dromara.common.mail.notify;

import java.nio.file.Path;
import java.util.List;

/**
 * MailBuilder 所需的渠道消息。
 */
public record MailNotificationMessage(
    List<String> to,
    List<String> cc,
    List<String> bcc,
    String subject,
    String content,
    boolean html,
    List<Path> attachments
) {

    public MailNotificationMessage {
        to = to == null ? List.of() : List.copyOf(to);
        cc = cc == null ? List.of() : List.copyOf(cc);
        bcc = bcc == null ? List.of() : List.copyOf(bcc);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    public MailNotificationMessage(List<String> to, List<String> cc, List<String> bcc,
                                   String subject, String content, boolean html) {
        this(to, cc, bcc, subject, content, html, List.of());
    }
}
