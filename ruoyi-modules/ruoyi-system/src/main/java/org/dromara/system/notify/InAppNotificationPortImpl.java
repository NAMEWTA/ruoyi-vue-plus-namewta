package org.dromara.system.notify;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.enums.PushSourceEnum;
import org.dromara.common.core.enums.PushTypeEnum;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.utils.IdGeneratorUtil;
import org.dromara.common.push.helper.PushHelper;
import org.dromara.notify.api.InAppNotificationPort;
import org.dromara.system.domain.SysMessage;
import org.dromara.system.domain.SysMessageRecipient;
import org.dromara.system.mapper.SysMessageMapper;
import org.dromara.system.mapper.SysMessageRecipientMapper;
import org.dromara.system.api.domain.PushPayloadDTO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * system 的站内收件箱适配器。
 *
 * <p>持久化和实时提示分成两个阶段，实时推送失败不影响收件箱事实。</p>
 */
@Service
@RequiredArgsConstructor
public class InAppNotificationPortImpl implements InAppNotificationPort {
    private final SysMessageMapper messageMapper;
    private final SysMessageRecipientMapper recipientMapper;

    @Override
    public void persist(String notificationId, InAppSnapshot snapshot, List<Long> userIds) {
        SysMessage message = new SysMessage();
        message.setMessageId(Long.valueOf(notificationId));
        message.setCategory("system");
        message.setType(PushTypeEnum.MESSAGE.getType());
        message.setSource(PushSourceEnum.BACKEND.getSource());
        message.setTitle(snapshot.title());
        message.setMessage(snapshot.content());
        message.setContent(snapshot.content());
        message.setPath(snapshot.path());
        message.setSendUserIds(userIds == null ? "" : StringUtils.joinComma(userIds));
        messageMapper.insert(message);
        LocalDateTime now = LocalDateTime.now();
        if (userIds != null) {
            for (Long userId : userIds) {
                SysMessageRecipient recipient = new SysMessageRecipient();
                recipient.setMessageRecipientId(IdGeneratorUtil.nextLongId());
                recipient.setMessageId(message.getMessageId());
                recipient.setUserId(userId);
                recipient.setCreateTime(now);
                recipientMapper.insert(recipient);
            }
        }
    }

    @Override
    public void pushRealtime(String notificationId, List<Long> userIds) {
        PushPayloadDTO payload = PushPayloadDTO.of(PushTypeEnum.MESSAGE, PushSourceEnum.BACKEND,
            "您有一条新通知", java.util.Map.of("notificationId", notificationId));
        if (userIds == null || userIds.isEmpty()) {
            PushHelper.publishAll(payload);
        } else {
            PushHelper.publishMessage(userIds, payload);
        }
    }

    @Override
    public void markEngagement(String notificationId, Long userId, boolean read) {
        SysMessageRecipient recipient = recipientMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysMessageRecipient>()
            .eq(SysMessageRecipient::getMessageId, Long.valueOf(notificationId))
            .eq(SysMessageRecipient::getUserId, userId));
        if (recipient == null) return;
        LocalDateTime now = LocalDateTime.now();
        if (recipient.getSeenTime() == null) recipient.setSeenTime(now);
        if (read) recipient.setReadTime(now);
        recipientMapper.updateById(recipient);
    }
}
