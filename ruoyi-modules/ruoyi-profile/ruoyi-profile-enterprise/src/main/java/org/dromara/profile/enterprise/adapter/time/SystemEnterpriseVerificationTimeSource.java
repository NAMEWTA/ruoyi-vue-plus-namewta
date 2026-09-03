package org.dromara.profile.enterprise.adapter.time;
import org.dromara.profile.enterprise.support.EnterpriseVerificationTimeSource;
import org.springframework.stereotype.Component;
import java.time.Instant;
/**
 * 承载SystemEnterpriseVerificationTimeSource业务规则的领域服务。
 */
@Component
public class SystemEnterpriseVerificationTimeSource implements EnterpriseVerificationTimeSource {
    /**
     * 获取当前时间
     */
    @Override
    public Instant now() {
        return Instant.now();
    }
}
