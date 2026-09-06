package org.dromara.notify.usecase;

import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import lombok.RequiredArgsConstructor;
import org.dromara.notify.api.CancelReceipt;
import org.dromara.notify.api.NotificationApplicationService;
import org.dromara.notify.api.NotificationCancelCommand;
import org.dromara.notify.api.NotificationCommand;
import org.dromara.notify.api.NotificationQuery;
import org.dromara.notify.api.NotificationReceipt;
import org.dromara.notify.api.NotificationRetryCommand;
import org.dromara.notify.api.NotificationSnapshot;
import org.dromara.notify.api.RetryReceipt;
import org.dromara.notify.service.runtime.NotificationApplicationRuntimeService;
import org.springframework.stereotype.Service;

/** 统一通知应用用例，集中承载事务和 public API 委托。 */
@Service
@RequiredArgsConstructor
public class NotificationApplicationUseCase implements NotificationApplicationService {
    private final NotificationApplicationRuntimeService runtimeService;

    @Override
    @DSTransactional
    public NotificationReceipt submit(NotificationCommand command) {
        return runtimeService.submit(command);
    }

    @Override
    public NotificationSnapshot query(NotificationQuery query) {
        return runtimeService.query(query);
    }

    @Override
    @DSTransactional
    public RetryReceipt retry(NotificationRetryCommand command) {
        return runtimeService.retry(command);
    }

    @Override
    @DSTransactional
    public CancelReceipt cancel(NotificationCancelCommand command) {
        return runtimeService.cancel(command);
    }
}
