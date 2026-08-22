package org.dromara.common.mail.notify;

import org.dromara.common.notify.attachment.NotifyAttachmentSnapshot;
import org.dromara.common.notify.exception.NotifyAttachmentSnapshotException;
import org.dromara.common.notify.exception.NotifyValidationException;
import org.dromara.common.notify.model.*;
import org.dromara.common.notify.spi.NotifyChannelAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
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
        Path tempDirectory = null;
        try {
            List<Path> attachments = List.of();
            if (!adapterRequest.attachments().isEmpty()) {
                tempDirectory = Files.createTempDirectory("notify-mail-");
                attachments = materialize(adapterRequest.attachments(), tempDirectory);
            }
            MailNotificationMessage message = buildMessage(request, attachments);
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
        } catch (IOException | InvalidPathException exception) {
            throw new NotifyAttachmentSnapshotException("SNAPSHOT_MATERIALIZE_FAILED",
                "通知附件快照物化失败", exception);
        } finally {
            deleteDirectory(tempDirectory);
        }
    }

    private List<Path> materialize(List<NotifyAttachmentSnapshot> snapshots, Path tempDirectory) throws IOException {
        List<Path> attachments = new ArrayList<>(snapshots.size());
        for (int index = 0; index < snapshots.size(); index++) {
            NotifyAttachmentSnapshot snapshot = snapshots.get(index);
            String fileName = safeFileName(snapshot.resource().fileName(), index);
            Path directory = Files.createDirectory(tempDirectory.resolve(String.valueOf(index)));
            Path target = directory.resolve(fileName);
            snapshot.resource().materializer().materialize(target);
            if (!Files.isRegularFile(target)) {
                throw new IOException("附件快照未生成文件");
            }
            attachments.add(target);
        }
        return List.copyOf(attachments);
    }

    private String safeFileName(String fileName, int index) {
        if (fileName == null || fileName.isBlank()) {
            return "attachment-" + index;
        }
        Path name = Path.of(fileName).getFileName();
        return name == null || name.toString().isBlank() ? "attachment-" + index : name.toString();
    }

    private void deleteDirectory(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 本地临时文件由操作系统兜底清理，不改变 Provider 同步结果。
                }
            });
        } catch (IOException ignored) {
            // 本地临时目录清理失败不改变 Provider 同步结果。
        }
    }

    private MailNotificationMessage buildMessage(NotifyRequest request, List<Path> attachments) {
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
        return new MailNotificationMessage(to, cc, bcc, content.subject(), content.contentSnapshot(), html,
            attachments);
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
}
