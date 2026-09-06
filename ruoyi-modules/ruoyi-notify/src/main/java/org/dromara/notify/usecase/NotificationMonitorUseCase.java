package org.dromara.notify.usecase;

import lombok.RequiredArgsConstructor;
import org.dromara.notify.api.NotificationApplicationService;
import org.dromara.notify.api.NotificationQuery;
import org.dromara.notify.api.NotificationSnapshot;
import org.dromara.notify.domain.vo.NotificationDeliveryView;
import org.dromara.notify.service.runtime.NotificationMonitorService;
import org.springframework.stereotype.Service;

import java.util.List;

/** 通知监控查询用例。 */
@Service
@RequiredArgsConstructor
public class NotificationMonitorUseCase {
    private final NotificationApplicationService notificationService;
    private final NotificationMonitorService monitorService;

    public NotificationSnapshot snapshot(String id) { return notificationService.query(new NotificationQuery(id, false)); }
    public List<NotificationDeliveryView> deliveries(Long userId, String channel, String status) {
        return monitorService.listDeliveries(userId, channel, status).stream().map(NotificationDeliveryView::from).toList();
    }
}

