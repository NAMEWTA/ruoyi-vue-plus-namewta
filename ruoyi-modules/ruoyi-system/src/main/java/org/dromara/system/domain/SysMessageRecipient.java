package org.dromara.system.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内消息收件关系与阅读状态。
 */
@Data
@TableName("sys_message_recipient")
public class SysMessageRecipient {
    @TableId private Long messageRecipientId;
    private Long messageId;
    private Long userId;
    private LocalDateTime seenTime;
    private LocalDateTime readTime;
    private LocalDateTime createTime;
}
