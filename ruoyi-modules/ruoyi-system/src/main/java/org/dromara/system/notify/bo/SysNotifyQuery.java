package org.dromara.system.notify.bo;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 全局通知监控筛选条件。clientPk 仅为显式审计筛选，不是行隔离条件。
 */
@Data
public class SysNotifyQuery {

    private String requestId;
    private String originalRequestId;
    private String bizType;
    private String bizId;
    private String channel;
    private String providerKey;
    private String status;
    private String providerMessageId;
    private String traceId;
    private Long clientPk;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime beginTime;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
