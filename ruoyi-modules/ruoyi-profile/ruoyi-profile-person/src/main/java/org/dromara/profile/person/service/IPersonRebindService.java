package org.dromara.profile.person.service;

import org.dromara.profile.person.domain.application.PersonRebindPublication;
import org.dromara.profile.person.domain.bo.PersonRebindConfirmBo;
import org.dromara.profile.person.domain.bo.PersonRebindMatchBo;
import org.dromara.profile.person.domain.bo.PersonRebindProbeBo;
import org.dromara.profile.person.domain.bo.PersonRebindSubmitBo;
import org.dromara.profile.person.domain.vo.PersonRebindConfirmationVo;
import org.dromara.profile.person.domain.vo.PersonRebindMatchVo;
import org.dromara.profile.person.domain.vo.PersonRebindProbeVo;
import org.dromara.profile.person.domain.vo.PersonRebindSubmissionVo;
import org.dromara.profile.person.domain.vo.PersonRebindUnbindVo;

import java.time.Instant;
import java.util.Optional;

public interface IPersonRebindService {

    PersonRebindProbeVo probe(PersonRebindProbeBo command);

    PersonRebindMatchVo match(long userId, PersonRebindMatchBo command);

    PersonRebindConfirmationVo confirm(long userId, PersonRebindConfirmBo command);

    PersonRebindSubmissionVo submit(long userId, PersonRebindSubmitBo command);

    PersonRebindUnbindVo unbind(long userId);

    Optional<PersonRebindPublication> publishApprovedRebind(long applicationId, int snapshotVersion,
                                                            Instant finishedTime);
}
