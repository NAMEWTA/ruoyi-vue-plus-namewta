package org.dromara.system.notify.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知列表安全投影，不包含正文和完整目标。
 */
@Data
public class SysNotifyListVo {

    private Long notifyLogId;
    private String requestId;
    private String originalRequestId;
    private String bizType;
    private String bizId;
    private String channel;
    private String providerKey;
    private String status;
    private String errorCode;
    private String errorMessage;
    private Long clientPk;
    private Long userId;
    private String traceId;
    private LocalDateTime createTime;
    private List<String> maskedTargets = List.of();
}
