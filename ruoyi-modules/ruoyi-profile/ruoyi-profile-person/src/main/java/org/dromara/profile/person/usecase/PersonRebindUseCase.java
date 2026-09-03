package org.dromara.profile.person.usecase;

import org.dromara.profile.person.domain.application.PersonRebindPublication;
import org.dromara.profile.person.domain.bo.*;
import org.dromara.profile.person.domain.vo.*;

import java.time.Instant;
import java.util.Optional;

/**
 * PersonRebindUseCase 应用用例合同，定义入口可调用的业务场景。
 */
public interface PersonRebindUseCase {

    /**
     * 编排 probe 应用用例。
     */
    PersonRebindProbeVo probe(PersonRebindProbeBo command);
    /**
     * 编排 match 应用用例。
     */
    PersonRebindMatchVo match(PersonRebindMatchBo command);
    /**
     * 编排 confirm 应用用例。
     */
    PersonRebindConfirmationVo confirm(PersonRebindConfirmBo command);
    /**
     * 编排 submit 应用用例。
     */
    PersonRebindSubmissionVo submit(PersonRebindSubmitBo command);
    /**
     * 编排 unbind 应用用例。
     */
    PersonRebindUnbindVo unbind();
    /**
     * 编排 publishApproved 应用用例。
     */
    Optional<PersonRebindPublication> publishApproved(long applicationId, int snapshotVersion, Instant finishedTime);
}
