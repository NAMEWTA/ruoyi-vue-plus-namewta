package org.dromara.profile.person.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.profile.person.domain.ProfilePersonBinding;
import lombok.Data;
import org.apache.ibatis.annotations.Param;
import org.dromara.profile.person.domain.vo.PersonApplicationRow;
import org.dromara.profile.person.domain.vo.PersonBindingEventRow;
import org.dromara.profile.person.domain.vo.PersonBindingRow;
import org.dromara.profile.person.domain.vo.PersonProfileRow;
import org.dromara.profile.person.domain.vo.PersonSubmissionRow;
import org.dromara.profile.person.domain.vo.PersonVersionRow;

import java.time.Instant;
import java.time.LocalDate;

public interface PersonRebindMapper extends BaseMapperPlus<ProfilePersonBinding, ProfilePersonBinding> {

    String selectProbeStatus(@Param("identityKey") String identityKey);

    RebindCandidateRow selectExactCandidate(@Param("fullName") String fullName,
                                            @Param("documentTypeCode") String documentTypeCode,
                                            @Param("documentNumber") String documentNumber,
                                            @Param("identityKey") String identityKey,
                                            @Param("gender") String gender,
                                            @Param("birthDate") LocalDate birthDate,
                                            @Param("validFrom") LocalDate validFrom,
                                            @Param("validUntil") LocalDate validUntil);

    PersonApplicationRow lockOpenApplication(@Param("userId") long userId);

    PersonApplicationRow lockApplication(@Param("applicationId") long applicationId);

    PersonBindingRow lockEffectiveBindingByUser(@Param("userId") long userId);

    RebindCandidateRow lockFrozenCandidate(@Param("profileId") long profileId,
                                           @Param("bindingId") long bindingId,
                                           @Param("bindingVersion") int bindingVersion);

    int confirmIntent(@Param("applicationId") long applicationId,
                      @Param("userId") long userId,
                      @Param("profileId") long profileId,
                      @Param("bindingId") long bindingId,
                      @Param("bindingVersion") int bindingVersion,
                      @Param("expectedVersion") int expectedVersion);

    PersonSubmissionRow lockSubmission(@Param("applicationId") long applicationId,
                                        @Param("submissionSeq") int submissionSeq);

    PersonProfileRow lockProfile(@Param("profileId") long profileId);

    PersonBindingRow lockExpectedBinding(@Param("bindingId") long bindingId,
                                         @Param("profileId") long profileId,
                                         @Param("bindingVersion") int bindingVersion);

    PersonVersionRow lockCurrentVersion(@Param("profileId") long profileId);

    int supersedeVersion(@Param("versionId") long versionId);

    int insertVersion(PersonVersionRow row);

    int updateProfile(PersonProfileRow row);

    int unbind(@Param("bindingId") long bindingId,
               @Param("bindingVersion") int bindingVersion,
               @Param("actorUserId") long actorUserId,
               @Param("occurredTime") Instant occurredTime);

    int insertBinding(PersonBindingRow row);

    int insertBindingEvent(PersonBindingEventRow row);

    int finishApplication(@Param("applicationId") long applicationId,
                          @Param("snapshotVersion") int snapshotVersion,
                          @Param("decisionVersion") int decisionVersion,
                          @Param("expectedVersion") int expectedVersion,
                          @Param("finishedTime") Instant finishedTime);

    @Data
    class RebindCandidateRow {
        private Long personProfileId;
        private String fullName;
        private String documentTypeCode;
        private String documentNumber;
        private String identityKey;
        private String gender;
        private LocalDate birthDate;
        private LocalDate validFrom;
        private LocalDate validUntil;
        private Long personBindingId;
        private Long oldUserId;
        private Integer bindingVersion;
    }
}
