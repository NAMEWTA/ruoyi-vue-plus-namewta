package org.dromara.profile.person.service.impl;

import org.dromara.profile.person.mapper.PersonNotificationAuditMapper;
import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import com.baomidou.dynamic.datasource.annotation.DsTxEventListener;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.notify.core.NotifyClient;
import org.dromara.common.notify.model.NotifyAuditPolicy;
import org.dromara.common.notify.model.NotifyChannel;
import org.dromara.common.notify.model.NotifyRequest;
import org.dromara.common.notify.model.NotifyResult;
import org.dromara.common.notify.model.NotifyStatus;
import org.dromara.common.notify.model.NotifyTarget;
import org.dromara.common.notify.model.NotifyTextContent;
import org.dromara.profile.person.mapper.PersonNotificationAuditMapper.NotificationAuditRow;
import org.dromara.profile.person.event.PersonReboundEvent;
import org.dromara.system.api.MessageService;
import org.dromara.system.api.UserService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PersonRebindNotificationService {

    static final String INTERNAL_TYPE = "PERSON_REBIND_INTERNAL";
    static final String SMS_TYPE = "PERSON_REBIND_SMS";
    static final String SAFE_TEXT = "您的个人实名认证绑定已变更。如非本人操作，请联系平台。";

    private final PersonNotificationAuditMapper audits;
    private final MessageService messages;
    private final UserService users;
    private final NotifyClient notifyClient;

    public void stage(PersonReboundEvent event) {
        if (event == null) {
            return;
        }
        stageOne(INTERNAL_TYPE, event.personProfileId(), event.personApplicationId(), event.oldUserId());
        stageOne(SMS_TYPE, event.personProfileId(), event.personApplicationId(), event.oldUserId());
    }

    @DsTxEventListener
    public void notifyOldAccount(PersonReboundEvent event) {
        if (event == null) {
            return;
        }
        deliverStaged(INTERNAL_TYPE, event.personProfileId(), event.personApplicationId(), event.oldUserId());
        deliverStaged(SMS_TYPE, event.personProfileId(), event.personApplicationId(), event.oldUserId());
    }

    @DSTransactional
    public boolean retryFailed(long auditId) {
        if (auditId <= 0) {
            return false;
        }
        NotificationAuditRow row = audits.lockRetryable(auditId);
        if (row == null || !isKnownType(row.getNotificationType())) {
            return false;
        }
        Delivery delivery = deliver(row.getNotificationType(), row.getProfileId(), row.getApplicationId(),
            row.getTargetUserId());
        row.setNotifyRequestId(delivery.requestId());
        row.setStatus(delivery.status());
        row.setFailureCategory(delivery.failureCategory());
        row.setOccurredTime(Instant.now());
        return audits.updateDelivery(row) == 1 && !"FAILED".equals(delivery.status());
    }

    private void stageOne(String type, long profileId, long applicationId, long userId) {
        NotificationAuditRow row = new NotificationAuditRow();
        row.setNotificationAuditId(IdWorker.getId());
        row.setNotificationType(type);
        row.setProfileId(profileId);
        row.setApplicationId(applicationId);
        row.setTargetUserId(userId);
        row.setNotifyRequestId(requestId(type, applicationId));
        row.setStatus("PENDING");
        row.setOccurredTime(Instant.now());
        if (audits.insertNotificationAudit(row) != 1) {
            throw new IllegalStateException("个人换绑通知待办写入失败");
        }
    }

    private void deliverStaged(String type, long profileId, long applicationId, long userId) {
        try {
            NotificationAuditRow row = audits.selectRetryable(type, profileId, applicationId, userId);
            if (row == null) {
                log.error("个人换绑通知待办不存在，channel={}，category=PENDING_AUDIT_MISSING", type);
                return;
            }
            Delivery delivery = deliver(type, profileId, applicationId, userId);
            row.setNotifyRequestId(delivery.requestId());
            row.setStatus(delivery.status());
            row.setFailureCategory(delivery.failureCategory());
            row.setOccurredTime(Instant.now());
            if (audits.updateDelivery(row) != 1) {
                log.error("个人换绑通知结果更新失败，channel={}，category=AUDIT_UPDATE_CONFLICT", type);
            }
        } catch (RuntimeException exception) {
            log.error("个人换绑通知处理失败，channel={}，category=DELIVERY_PROCESS_FAILED", type);
        }
    }

    private Delivery deliver(String type, long profileId, long applicationId, long userId) {
        try {
            if (INTERNAL_TYPE.equals(type)) {
                messages.sendMessage(userId, SAFE_TEXT);
                return new Delivery(internalRequestId(applicationId), "ACCEPTED", null);
            }
            if (SMS_TYPE.equals(type)) {
                return sendSms(profileId, applicationId, userId);
            }
            return new Delivery(null, "FAILED", "UNSUPPORTED_CHANNEL");
        } catch (RuntimeException exception) {
            log.warn("个人换绑通知失败，channel={}，category=DELIVERY_FAILED", type);
            return new Delivery(requestId(type, applicationId), "FAILED", "DELIVERY_FAILED");
        }
    }

    private Delivery sendSms(long profileId, long applicationId, long userId) {
        String phone = text(users.selectPhonenumberById(userId));
        String requestId = requestId(SMS_TYPE, applicationId);
        if (phone == null) {
            return new Delivery(requestId, "SKIPPED", "TARGET_PHONE_UNAVAILABLE");
        }
        NotifyResult result = notifyClient.send(NotifyRequest.builder()
            .requestId(requestId)
            .bizType("profile_person_rebind")
            .bizId(Long.toString(applicationId))
            .channel(NotifyChannel.SMS)
            .targets(List.of(NotifyTarget.phone(phone)))
            .content(new NotifyTextContent("实名认证绑定变更通知", SAFE_TEXT))
            .auditPolicy(NotifyAuditPolicy.REDACT_SENSITIVE)
            .idempotencyKey("profile:person:rebind:" + profileId + ":" + applicationId + ":sms")
            .idempotencyWindow(Duration.ofDays(30))
            .build());
        if (result == null || result.status() == null) {
            return new Delivery(requestId, "FAILED", "EMPTY_NOTIFY_RESULT");
        }
        if (result.status() == NotifyStatus.ACCEPTED) {
            return new Delivery(result.requestId(), "ACCEPTED", null);
        }
        if (result.status() == NotifyStatus.SKIPPED_DUPLICATE) {
            return new Delivery(result.requestId(), "SKIPPED", null);
        }
        return new Delivery(result.requestId(), "FAILED", "NOTIFY_" + result.status().name());
    }

    private String internalRequestId(long applicationId) {
        return "internal-person-rebind-" + applicationId;
    }

    private String requestId(String type, long applicationId) {
        return (SMS_TYPE.equals(type) ? "sms" : "notify") + "-person-rebind-" + applicationId;
    }

    private boolean isKnownType(String type) {
        return INTERNAL_TYPE.equals(type) || SMS_TYPE.equals(type);
    }

    private String text(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    private record Delivery(String requestId, String status, String failureCategory) {
    }
}
