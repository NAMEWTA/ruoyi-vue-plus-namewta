package org.dromara.third.service;

import org.dromara.third.domain.bo.ThirdCredentialBo;
import org.dromara.third.domain.vo.ThirdCredentialVo;

import java.util.List;

public interface ThirdCredentialUseCase {
    List<ThirdCredentialVo> list(String providerCode, String endpointCode);

    void save(ThirdCredentialBo bo);

    void remove(Long credentialId);
}
