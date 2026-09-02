package org.dromara.profile.person.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.profile.person.domain.ProfileNotificationAudit;
import lombok.Data;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;

public interface PersonNotificationAuditMapper extends BaseMapperPlus<ProfileNotificationAudit, ProfileNotificationAudit> {

    int insertNotificationAudit(NotificationAuditRow row);

    NotificationAuditRow selectRetryable(@Param("notificationType") String notificationType,
                                         @Param("profileId") long profileId,
                                         @Param("applicationId") long applicationId,
                                         @Param("targetUserId") long targetUserId);

    NotificationAuditRow lockRetryable(@Param("auditId") long auditId);

    int updateDelivery(NotificationAuditRow row);

    @Data
    class NotificationAuditRow {
        private Long notificationAuditId;
        private String notificationType;
        private Long profileId;
        private Long applicationId;
        private Long targetUserId;
        private String notifyRequestId;
        private String status;
        private String failureCategory;
        private Instant occurredTime;
        private Integer version;
    }
}
