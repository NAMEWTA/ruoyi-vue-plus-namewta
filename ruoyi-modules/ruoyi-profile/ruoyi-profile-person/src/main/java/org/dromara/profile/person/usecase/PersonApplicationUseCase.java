package org.dromara.profile.person.usecase;

import org.dromara.profile.person.domain.bo.PersonApplicationSaveBo;
import org.dromara.profile.person.domain.vo.PersonApplicationVo;

/**
 * PersonApplicationUseCase 应用用例合同，定义入口可调用的业务场景。
 */
public interface PersonApplicationUseCase {

    /**
     * 编排 current 应用用例。
     */
    PersonApplicationVo current();

    /**
     * 编排 save 应用用例。
     */
    PersonApplicationVo save(PersonApplicationSaveBo command);

    /**
     * 编排 submit 应用用例。
     */
    PersonApplicationVo submit(int expectedVersion);
}
