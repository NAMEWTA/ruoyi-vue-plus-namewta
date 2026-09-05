package org.dromara.notify.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 用户与渠道构成的通知投递。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notify_delivery")
public class NotifyDelivery extends BaseEntity {
    @TableId private Long deliveryId;
    private Long intentId;
    private Long recipientId;
    private Long userId;
    private String channel;
    private String targetValue;
    private String status;
    private Integer attemptCount;
    private String providerMessageId;
    private String errorCode;
    private String errorMessage;
    private String acceptedAt;
    private String deliveredAt;
    private String readAt;
    private Integer version;
}
