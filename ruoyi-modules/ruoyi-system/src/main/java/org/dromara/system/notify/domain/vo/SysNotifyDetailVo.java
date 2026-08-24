package org.dromara.system.notify.domain.vo;

import lombok.Data;
import org.dromara.system.notify.domain.SysNotifyDeliveryLog;
import org.dromara.system.notify.domain.SysNotifyLog;

import java.util.List;

/**
 * 通知完整详情。调用入口必须具备 system:notify:query 权限。
 */
@Data
public class SysNotifyDetailVo {

    private SysNotifyLog notification;
    private List<SysNotifyDeliveryLog> deliveries = List.of();
    private List<Long> attachmentOssIds = List.of();
}
