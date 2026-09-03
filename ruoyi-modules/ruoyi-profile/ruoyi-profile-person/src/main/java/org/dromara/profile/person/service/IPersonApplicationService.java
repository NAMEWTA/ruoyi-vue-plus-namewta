package org.dromara.profile.person.service;

import org.dromara.profile.person.domain.application.PersonDocumentTypeRule;
import org.dromara.profile.person.domain.application.PersonPublication;
import org.dromara.profile.person.domain.application.PersonSubmission;
import org.dromara.profile.person.domain.bo.PersonApplicationSaveBo;
import org.dromara.profile.person.domain.vo.PersonApplicationVo;

import java.time.Instant;
import java.util.Optional;

/**
 * 承载IPersonApplicationService业务规则的领域服务。
 */
public interface IPersonApplicationService extends org.dromara.profile.person.port.PersonApplicationPublicationPort {

    /**
     * 查询当前用户的进行中申请
     */
    Optional<PersonApplicationVo> current(long userId);

    /**
     * 保存业务申请数据
     */
    PersonApplicationVo save(long userId, PersonApplicationSaveBo command);

    /**
     * 提交申请并启动后续流程
     */
    PersonApplicationVo submit(long userId, int expectedVersion);

}
