package org.dromara.profile.enterprise.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.profile.enterprise.domain.ProfileEnterpriseTransferRecord;
import org.dromara.profile.enterprise.domain.vo.EnterpriseProfileBindingVo;

import org.dromara.profile.enterprise.domain.vo.EnterpriseTransferOwnerRow;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;

public interface EnterpriseTransferMapper
    extends BaseMapperPlus<ProfileEnterpriseTransferRecord, EnterpriseProfileBindingVo> {

    EnterpriseTransferOwnerRow selectActiveOwner(@Param("userId") long userId);

    EnterpriseTransferOwnerRow lockActiveOwner(@Param("userId") long userId);

    int countEffectiveBinding(@Param("userId") long userId);

    int insertTransferRecord(@Param("recordId") long recordId,
                             @Param("profileId") long profileId,
                             @Param("sourceBindingId") long sourceBindingId,
                             @Param("sourceUserId") long sourceUserId,
                             @Param("targetUserId") long targetUserId,
                             @Param("challengeId") String challengeId,
                             @Param("bindingVersion") int bindingVersion,
                             @Param("expiresTime") Instant expiresTime,
                             @Param("occurredTime") Instant occurredTime);

    int confirmTransferRecord(@Param("challengeId") String challengeId,
                              @Param("profileId") long profileId,
                              @Param("sourceBindingId") long sourceBindingId,
                              @Param("sourceUserId") long sourceUserId,
                              @Param("targetUserId") long targetUserId,
                              @Param("bindingVersion") int bindingVersion,
                              @Param("occurredTime") Instant occurredTime,
                              @Param("operatorId") long operatorId);

    Long lockEffectiveBindingId(@Param("userId") long userId);

    int unbindSource(@Param("bindingId") long bindingId,
                     @Param("profileId") long profileId,
                     @Param("userId") long userId,
                     @Param("bindingVersion") int bindingVersion,
                     @Param("occurredTime") Instant occurredTime,
                     @Param("operatorId") long operatorId);

    int insertBinding(@Param("bindingId") long bindingId,
                      @Param("profileId") long profileId,
                      @Param("userId") long userId,
                      @Param("sourceType") String sourceType,
                      @Param("sourceId") long sourceId,
                      @Param("occurredTime") Instant occurredTime,
                      @Param("operatorId") long operatorId);

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
