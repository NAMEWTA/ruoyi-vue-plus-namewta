package org.dromara.profile.enterprise.service;

import org.dromara.profile.enterprise.domain.application.EnterpriseDocumentTypeRule;
import org.dromara.profile.enterprise.domain.application.EnterprisePublication;
import org.dromara.profile.enterprise.domain.application.EnterpriseSubmission;
import org.dromara.profile.enterprise.domain.vo.EnterpriseApplicationVo;
import org.dromara.profile.enterprise.domain.bo.EnterpriseApplicationSaveBo;
import org.dromara.profile.enterprise.domain.bo.EnterpriseApplicationProbeBo;
import org.dromara.profile.enterprise.domain.vo.EnterpriseApplicationProbeVo;

import java.util.Optional;

public interface IEnterpriseApplicationService {

    Optional<EnterpriseApplicationVo> current(long userId);

    EnterpriseApplicationProbeVo probe(EnterpriseApplicationProbeBo command);

    EnterpriseApplicationVo save(long userId, EnterpriseApplicationSaveBo command);

    EnterpriseApplicationVo submit(long userId, int expectedVersion);

    Optional<EnterpriseDocumentTypeRule> findDocumentType(String documentTypeCode);

    EnterpriseSubmission requireSubmission(long applicationId, int submissionSeq);

    EnterprisePublication publishApproved(long applicationId, int snapshotVersion, java.time.Instant finishedTime);
}
