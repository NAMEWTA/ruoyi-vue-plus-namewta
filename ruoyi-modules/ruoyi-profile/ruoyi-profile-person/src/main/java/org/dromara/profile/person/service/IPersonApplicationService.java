package org.dromara.profile.person.service;

import org.dromara.profile.person.domain.application.PersonDocumentTypeRule;
import org.dromara.profile.person.domain.application.PersonPublication;
import org.dromara.profile.person.domain.application.PersonSubmission;
import org.dromara.profile.person.domain.bo.PersonApplicationSaveBo;
import org.dromara.profile.person.domain.vo.PersonApplicationVo;

import java.time.Instant;
import java.util.Optional;

public interface IPersonApplicationService {

    Optional<PersonApplicationVo> current(long userId);

    PersonApplicationVo save(long userId, PersonApplicationSaveBo command);

    PersonApplicationVo submit(long userId, int expectedVersion);

    Optional<PersonDocumentTypeRule> findDocumentType(String documentTypeCode);

    PersonSubmission requireSubmission(long applicationId, int submissionSeq);

    PersonPublication publishApproved(long applicationId, int snapshotVersion, Instant finishedTime);
}
