package org.dromara.profile.person.notification;

import lombok.Data;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

public interface PersonNotificationAuditMapper {

    @Insert("""
        insert into profile_notification_audit (
            notification_audit_id, notification_type, profile_type, profile_id, application_id,
            target_user_id, notify_request_id, status, failure_category, occurred_time, version,
            create_dept, create_time, create_by, update_time, update_by, del_flag
        ) values (
            #{notificationAuditId}, #{notificationType}, 'PERSON', #{profileId}, #{applicationId},
            #{targetUserId}, #{notifyRequestId}, #{status}, #{failureCategory}, #{occurredTime}, 0,
            -1, current_timestamp, -1, current_timestamp, -1, '0'
        )
        """)
    int insert(NotificationAuditRow row);

    @Select("""
        select notification_audit_id, notification_type, profile_id, application_id,
               target_user_id, notify_request_id, status, failure_category, occurred_time, version
          from profile_notification_audit
         where notification_type = #{notificationType} and profile_type = 'PERSON'
           and profile_id = #{profileId} and application_id = #{applicationId}
           and target_user_id = #{targetUserId} and status in ('PENDING', 'FAILED')
           and del_flag = '0'
         order by occurred_time asc, notification_audit_id asc
         limit 1
        """)
    NotificationAuditRow selectRetryable(@Param("notificationType") String notificationType,
                                         @Param("profileId") long profileId,
                                         @Param("applicationId") long applicationId,
                                         @Param("targetUserId") long targetUserId);

    @Select("""
        select notification_audit_id, notification_type, profile_id, application_id,
               target_user_id, notify_request_id, status, failure_category, occurred_time, version
          from profile_notification_audit
         where notification_audit_id = #{auditId} and profile_type = 'PERSON'
           and status in ('PENDING', 'FAILED') and del_flag = '0'
         for update
        """)
    NotificationAuditRow lockRetryable(@Param("auditId") long auditId);

    @Update("""
        update profile_notification_audit
           set notify_request_id = #{notifyRequestId}, status = #{status},
               failure_category = #{failureCategory}, occurred_time = #{occurredTime},
               version = version + 1, update_time = current_timestamp, update_by = -1
         where notification_audit_id = #{notificationAuditId} and status in ('PENDING', 'FAILED')
           and version = #{version} and del_flag = '0'
        """)
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
