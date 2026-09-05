package org.dromara.notify.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 通知意图事实。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notify_intent")
public class NotifyIntent extends BaseEntity {
    @TableId private Long intentId;
    private String appId;
    private String sceneCode;
    private String bizType;
    private String bizId;
    private String templateCode;
    private String templateParamsJson;
    private String strategy;
    private String mode;
    private Integer priority;
    private String scheduledAt;
    private String expiresAt;
    private String idempotencyKey;
    private String status;
    private String titleSnapshot;
    private String contentSnapshot;
    private String pathSnapshot;
    private String metadataJson;
    private Integer version;
}
