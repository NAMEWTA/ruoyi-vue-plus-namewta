package org.dromara.profile.person.usecase.impl;
import com.baomidou.dynamic.datasource.annotation.DSTransactional;

import lombok.RequiredArgsConstructor;
import org.dromara.profile.person.domain.bo.PersonApplicationSaveBo;
import org.dromara.profile.person.domain.application.PersonApplicationProcessCommand;
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
    @DSTransactional
    @Override
    public PersonApplicationVo current(long userId) {
        return service.current(userId);
    }

    /**
     * 编排 save 应用用例。
     */
    @DSTransactional
    @Override
    public PersonApplicationVo save(long userId, PersonApplicationSaveBo command) {
        return service.save(userId, command);
    }

    /**
     * 编排 submit 应用用例。
     */
    @DSTransactional
    @Override
    public PersonApplicationVo submit(long userId, int expectedVersion) {
        return service.submit(userId, expectedVersion);
    }

    /** 将工作流事件交给 Service 处理。 */
    @DSTransactional
    @Override
    public void handleProcess(PersonApplicationProcessCommand command) {
        service.handleProcess(command);
    }
}
