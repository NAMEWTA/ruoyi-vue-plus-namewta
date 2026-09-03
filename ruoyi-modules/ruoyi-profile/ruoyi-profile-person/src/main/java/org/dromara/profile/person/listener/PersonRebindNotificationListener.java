package org.dromara.profile.person.listener;

import com.baomidou.dynamic.datasource.annotation.DsTxEventListener;
import lombok.RequiredArgsConstructor;
import org.dromara.profile.person.event.PersonReboundEvent;
import org.dromara.profile.person.usecase.PersonRebindNotificationUseCase;
import org.springframework.stereotype.Service;

/** 个人换绑通知事务事件监听器。 */
@Service
@RequiredArgsConstructor
public class PersonRebindNotificationListener {

    private final PersonRebindNotificationUseCase useCase;

    /** 事务提交后触发通知应用用例。 */
    @DsTxEventListener
    public void handle(PersonReboundEvent event) {
        useCase.notifyOldAccount(event);
    }
}
