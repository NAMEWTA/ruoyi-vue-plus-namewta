package org.dromara.profile.enterprise.transfer.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

public interface EnterpriseTransferMapper {

    @Select("""
        select b.enterprise_binding_id as binding_id, b.enterprise_profile_id as profile_id,
               b.user_id, b.binding_version
          from profile_enterprise_binding b
          join profile_enterprise p on p.enterprise_profile_id = b.enterprise_profile_id
         where b.user_id = #{userId} and b.status = 'ACTIVE' and b.del_flag = '0'
           and p.status = 'ACTIVE' and p.del_flag = '0'
        """)
    EnterpriseTransferOwnerRow selectActiveOwner(@Param("userId") long userId);

    @Select("""
        select b.enterprise_binding_id as binding_id, b.enterprise_profile_id as profile_id,
               b.user_id, b.binding_version
          from profile_enterprise_binding b
          join profile_enterprise p on p.enterprise_profile_id = b.enterprise_profile_id
         where b.user_id = #{userId} and b.status = 'ACTIVE' and b.del_flag = '0'
           and p.status = 'ACTIVE' and p.del_flag = '0'
         for update
        """)
    EnterpriseTransferOwnerRow lockActiveOwner(@Param("userId") long userId);

    @Select("""
        select u.user_id, p.person_profile_id, u.phone_number as phone
          from sys_user u
          join profile_person_binding b on b.user_id = u.user_id
          join profile_person p on p.person_profile_id = b.person_profile_id
         where p.full_name = #{fullName}
           and upper(right(trim(p.document_number), 4)) = #{documentLastFour}
           and u.phone_number = #{phone}
           and b.status = 'ACTIVE' and b.del_flag = '0'
           and p.status = 'ACTIVE' and p.del_flag = '0'
           and u.del_flag = '0'
        """)
    List<EnterpriseTransferTargetRow> selectExactTargets(@Param("fullName") String fullName,
                                                         @Param("documentLastFour") String documentLastFour,
                                                         @Param("phone") String phone);

    @Select("""
        select u.user_id, p.person_profile_id, u.phone_number as phone
          from sys_user u
          join profile_person_binding b on b.user_id = u.user_id
          join profile_person p on p.person_profile_id = b.person_profile_id
         where u.user_id = #{userId} and p.person_profile_id = #{personProfileId}
           and p.full_name = #{fullName}
           and upper(right(trim(p.document_number), 4)) = #{documentLastFour}
           and u.phone_number = #{phone} and u.status = '0' and u.del_flag = '0'
           and b.status = 'ACTIVE' and b.del_flag = '0'
           and p.status = 'ACTIVE' and p.del_flag = '0'
         for update
        """)
    EnterpriseTransferTargetRow lockExactTarget(@Param("userId") long userId,
                                                 @Param("personProfileId") long personProfileId,
                                                 @Param("fullName") String fullName,
                                                 @Param("documentLastFour") String documentLastFour,
                                                 @Param("phone") String phone);

    @Select("""
        select count(1)
          from profile_enterprise_binding
         where user_id = #{userId} and status in ('ACTIVE','SUSPENDED') and del_flag = '0'
        """)
    int countEffectiveBinding(@Param("userId") long userId);

    @Insert("""
        insert into profile_enterprise_transfer_record (
            enterprise_transfer_record_id, enterprise_profile_id, source_binding_id,
            source_user_id, target_user_id, challenge_id, expected_binding_version,
            status, failed_attempts, expires_time, version,
            create_dept, create_time, create_by, update_time, update_by, del_flag
        ) values (
            #{recordId}, #{profileId}, #{sourceBindingId},
            #{sourceUserId}, #{targetUserId}, #{challengeId}, #{bindingVersion},
            'CHALLENGED', 0, #{expiresTime}, 0,
            -1, #{occurredTime}, #{sourceUserId}, #{occurredTime}, #{sourceUserId}, '0'
        )
        """)
    int insertTransferRecord(@Param("recordId") long recordId,
                             @Param("profileId") long profileId,
                             @Param("sourceBindingId") long sourceBindingId,
                             @Param("sourceUserId") long sourceUserId,
                             @Param("targetUserId") long targetUserId,
                             @Param("challengeId") String challengeId,
                             @Param("bindingVersion") int bindingVersion,
                             @Param("expiresTime") Instant expiresTime,
                             @Param("occurredTime") Instant occurredTime);

    @Update("""
        update profile_enterprise_transfer_record
           set status = 'CONFIRMED', confirmed_time = #{occurredTime},
               version = version + 1, update_time = current_timestamp, update_by = #{operatorId}
         where challenge_id = #{challengeId} and enterprise_profile_id = #{profileId}
           and source_binding_id = #{sourceBindingId} and source_user_id = #{sourceUserId}
           and target_user_id = #{targetUserId} and expected_binding_version = #{bindingVersion}
           and status = 'CHALLENGED' and del_flag = '0'
        """)
    int confirmTransferRecord(@Param("challengeId") String challengeId,
                              @Param("profileId") long profileId,
                              @Param("sourceBindingId") long sourceBindingId,
                              @Param("sourceUserId") long sourceUserId,
                              @Param("targetUserId") long targetUserId,
                              @Param("bindingVersion") int bindingVersion,
                              @Param("occurredTime") Instant occurredTime,
                              @Param("operatorId") long operatorId);

    @Select("""
        select enterprise_binding_id
          from profile_enterprise_binding
         where user_id = #{userId} and status in ('ACTIVE','SUSPENDED') and del_flag = '0'
         for update
        """)
    Long lockEffectiveBindingId(@Param("userId") long userId);

    @Update("""
        update profile_enterprise_binding
           set status = 'UNBOUND', binding_version = binding_version + 1,
               unbound_time = #{occurredTime}, update_time = current_timestamp, update_by = #{operatorId}
         where enterprise_binding_id = #{bindingId} and enterprise_profile_id = #{profileId}
           and user_id = #{userId} and binding_version = #{bindingVersion}
           and status = 'ACTIVE' and del_flag = '0'
        """)
    int unbindSource(@Param("bindingId") long bindingId,
                     @Param("profileId") long profileId,
                     @Param("userId") long userId,
                     @Param("bindingVersion") int bindingVersion,
                     @Param("occurredTime") Instant occurredTime,
                     @Param("operatorId") long operatorId);

    @Insert("""
        insert into profile_enterprise_binding (
            enterprise_binding_id, enterprise_profile_id, user_id, status, binding_version,
            source_type, source_id, bound_time, version,
            create_dept, create_time, create_by, update_time, update_by, del_flag
        ) values (
            #{bindingId}, #{profileId}, #{userId}, 'ACTIVE', 1,
            #{sourceType}, #{sourceId}, #{occurredTime}, 0,
            -1, current_timestamp, #{operatorId}, current_timestamp, #{operatorId}, '0'
        )
        """)
    int insertBinding(@Param("bindingId") long bindingId,
                      @Param("profileId") long profileId,
                      @Param("userId") long userId,
                      @Param("sourceType") String sourceType,
                      @Param("sourceId") long sourceId,
                      @Param("occurredTime") Instant occurredTime,
                      @Param("operatorId") long operatorId);

    @Insert("""
        insert into profile_enterprise_binding_event (
            enterprise_binding_event_id, enterprise_binding_id, enterprise_profile_id, user_id,
            event_type, binding_version, source_type, source_id, reason, occurred_time,
            version, create_dept, create_time, create_by, update_time, update_by, del_flag
        ) values (
            #{eventId}, #{bindingId}, #{profileId}, #{userId},
            #{eventType}, #{bindingVersion}, #{sourceType}, #{sourceId}, #{reason}, #{occurredTime},
            0, -1, current_timestamp, #{operatorId}, current_timestamp, #{operatorId}, '0'
        )
        """)
    int insertEvent(@Param("eventId") long eventId,
                    @Param("bindingId") long bindingId,
                    @Param("profileId") long profileId,
                    @Param("userId") long userId,
                    @Param("eventType") String eventType,
                    @Param("bindingVersion") int bindingVersion,
                    @Param("sourceType") String sourceType,
                    @Param("sourceId") long sourceId,
                    @Param("reason") String reason,
                    @Param("occurredTime") Instant occurredTime,
                    @Param("operatorId") long operatorId);
}
