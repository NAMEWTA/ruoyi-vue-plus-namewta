package org.dromara.notify.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.notify.api.NotificationQuery;
import org.dromara.notify.api.NotificationSnapshot;
import org.dromara.notify.api.NotificationApplicationService;
import org.dromara.notify.domain.entity.NotifyDelivery;
import org.dromara.notify.mapper.NotifyDeliveryMapper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 通知统一监控接口，提供跨渠道的发送、接收人和供应商状态查询。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/notify/monitor")
public class NotificationMonitorController {

    private final NotificationApplicationService notificationService;
    private final NotifyDeliveryMapper deliveryMapper;

    /**
     * 查询通知详情。
     *
     * @param notificationId 通知编号
     * @return 通知快照
     */
    @SaCheckPermission("notify:monitor:query")
    @GetMapping("/snapshot")
    public R<NotificationSnapshot> snapshot(@RequestParam String notificationId) {
        return R.ok(notificationService.query(new NotificationQuery(notificationId, false)));
    }

    /**
     * 按接收人和渠道查询投递日志。
     *
     * @param userId 用户编号，可选
     * @param channel 渠道，可选
     * @param status 状态，可选
     * @return 投递日志
     */
    @SaCheckPermission("notify:monitor:list")
    @GetMapping("/deliveries")
    public R<List<NotifyDelivery>> deliveries(@RequestParam(required = false) Long userId,
                                              @RequestParam(required = false) String channel,
                                              @RequestParam(required = false) String status) {
        LambdaQueryWrapper<NotifyDelivery> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(userId != null, NotifyDelivery::getUserId, userId)
            .eq(channel != null && !channel.isBlank(), NotifyDelivery::getChannel, channel)
            .eq(status != null && !status.isBlank(), NotifyDelivery::getStatus, status)
            .orderByDesc(NotifyDelivery::getCreateTime)
            .last("limit 500");
        return R.ok(deliveryMapper.selectList(wrapper));
    }
}
