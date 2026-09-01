package org.dromara.profile.enterprise.transfer;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class EnterpriseTransferCodeGenerator {

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        return String.format("%06d", random.nextInt(1_000_000));
    }
}
