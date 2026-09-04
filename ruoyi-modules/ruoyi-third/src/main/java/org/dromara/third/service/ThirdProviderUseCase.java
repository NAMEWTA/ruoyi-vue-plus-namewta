package org.dromara.third.service;

import org.dromara.third.domain.bo.ThirdProviderBo;
import org.dromara.third.domain.vo.ThirdProviderVo;

import java.util.List;

public interface ThirdProviderUseCase {
    List<ThirdProviderVo> list(String keyword);
    ThirdProviderVo get(Long providerId);
    void save(ThirdProviderBo bo);
    void changeStatus(Long providerId, String status);
    void remove(Long providerId);
}
