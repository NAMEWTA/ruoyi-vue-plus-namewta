package org.dromara.system.notify.attachment;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import lombok.RequiredArgsConstructor;
import org.dromara.common.notify.attachment.NotifyLogIdGenerator;
import org.springframework.stereotype.Component;

/**
 * 使用系统统一雪花生成器预分配通知日志主键。
 */
@Component
@RequiredArgsConstructor
public class SystemNotifyLogIdGenerator implements NotifyLogIdGenerator {

    private final IdentifierGenerator identifierGenerator;

    @Override
    public long nextId() {
        return identifierGenerator.nextId(null).longValue();
    }
}
