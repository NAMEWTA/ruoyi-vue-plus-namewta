package org.dromara.third.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.third.domain.bo.ThirdCredentialBo;
import org.dromara.third.domain.vo.ThirdCredentialVo;
import org.dromara.third.service.ThirdCredentialService;
import org.dromara.third.usecase.ThirdCredentialUseCase;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ThirdCredentialUseCaseImpl implements ThirdCredentialUseCase {
    private final ThirdCredentialService service;

    public List<ThirdCredentialVo> list(String providerCode, String endpointCode) { return service.list(providerCode, endpointCode); }
    public void save(ThirdCredentialBo bo) { service.save(bo); }
    public void remove(Long credentialId) { service.remove(credentialId); }
}
