package org.dromara.profile.person.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.profile.person.domain.bo.PersonApplicationSaveBo;
import org.dromara.profile.person.domain.vo.PersonApplicationVo;
import org.dromara.profile.person.service.PersonApplicationService;
import org.dromara.profile.person.usecase.PersonApplicationUseCase;
import org.springframework.stereotype.Service;

/**
 * PersonApplicationUseCaseImpl 应用用例合同，定义入口可调用的业务场景。
 */
@Service
@RequiredArgsConstructor
public class PersonApplicationUseCaseImpl implements PersonApplicationUseCase {

    private final PersonApplicationService service;

    /**
     * 编排 current 应用用例。
     */
    @Override
    public PersonApplicationVo current() {
        return service.current(LoginHelper.getUserId()).orElse(null);
    }

    /**
     * 编排 save 应用用例。
     */
    @Override
    public PersonApplicationVo save(PersonApplicationSaveBo command) {
        return service.save(LoginHelper.getUserId(), command);
    }

    /**
     * 编排 submit 应用用例。
     */
    @Override
    public PersonApplicationVo submit(int expectedVersion) {
        return service.submit(LoginHelper.getUserId(), expectedVersion);
    }
}
