package org.dromara.system.notify.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 单个物理目标的一次 Provider 调用结果。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_notify_delivery_log")
public class SysNotifyDeliveryLog extends BaseEntity {

    @TableId("notify_delivery_log_id")
    private Long notifyDeliveryLogId;
    private Long notifyLogId;
    private String targetType;
    private String targetRole;
    private String targetValue;
    private String providerKey;
    private String providerMessageId;
    private Integer attemptNo;
    private String status;
    private Long costTime;
    private String errorCode;
    private String errorMessage;

    @Version
    private Integer version;

    @TableLogic
    private String delFlag;
}
