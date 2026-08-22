package org.dromara.system.notify.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 一次逻辑通知的完整监控快照。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_notify_log")
public class SysNotifyLog extends BaseEntity {

    @TableId("notify_log_id")
    private Long notifyLogId;
    private String requestId;
    private String originalRequestId;
    private String bizType;
    private String bizId;
    private String channel;
    private String providerKey;
    private String subject;
    private String content;
    private String contentType;
    private String templateCode;
    private String templateParams;
    private String contentSnapshot;
    private String attachmentOssIds;
    private String status;
    private String errorCode;
    private String errorMessage;
    private Long clientPk;
    private Long userId;
    private String traceId;

    @Version
    private Integer version;

    @TableLogic
    private String delFlag;
}
