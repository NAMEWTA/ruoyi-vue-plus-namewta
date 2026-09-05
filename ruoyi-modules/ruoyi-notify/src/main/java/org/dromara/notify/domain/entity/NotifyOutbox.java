package org.dromara.notify.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 可靠异步通知任务。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notify_outbox")
public class NotifyOutbox extends BaseEntity {
    @TableId private Long outboxId;
    private Long intentId;
    private Long deliveryId;
    private String status;
    private String availableAt;
    private Integer attemptCount;
    private String nextAttemptAt;
    private String leaseOwner;
    private String leaseUntil;
    private Integer maxAttempts;
    private String lastErrorCode;
    private String lastErrorMessage;
}
