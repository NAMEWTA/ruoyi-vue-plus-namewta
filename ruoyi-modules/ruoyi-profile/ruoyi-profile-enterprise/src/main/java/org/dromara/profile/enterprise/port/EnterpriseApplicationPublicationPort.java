package org.dromara.profile.enterprise.port;

import org.dromara.profile.enterprise.domain.application.EnterpriseDocumentTypeRule;
import org.dromara.profile.enterprise.domain.application.EnterprisePublication;
import org.dromara.profile.enterprise.domain.application.EnterpriseSubmission;

import java.time.Instant;
import java.util.Optional;

/** EnterpriseApplicationPublicationPort 业务端口合同，隔离外部适配实现。  Narrow application capability consumed by the enterprise administration workflow. */
public interface EnterpriseApplicationPublicationPort {

    /**
     * 处理 findDocumentType 业务步骤。
     */
    Optional<EnterpriseDocumentTypeRule> findDocumentType(String documentTypeCode);

    /**
     * 处理 requireSubmission 业务步骤。
     */
    EnterpriseSubmission requireSubmission(long applicationId, int submissionSeq);

    /**
     * 处理 publishApproved 业务步骤。
     */
    EnterprisePublication publishApproved(long applicationId, int snapshotVersion, Instant finishedTime);
}
