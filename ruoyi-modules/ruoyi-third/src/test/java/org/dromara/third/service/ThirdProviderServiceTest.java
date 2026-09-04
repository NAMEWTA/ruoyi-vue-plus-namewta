package org.dromara.third.service;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.third.dao.ThirdProviderDao;
import org.dromara.third.domain.ThirdProvider;
import org.dromara.third.domain.bo.ThirdProviderBo;
import org.dromara.third.port.ThirdConfigSnapshotPort;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("local")
class ThirdProviderServiceTest {

    @Test
    void rejectsProviderCodeRenameBeforePersistenceOrCacheEviction() {
        ThirdProviderDao providerDao = mock(ThirdProviderDao.class);
        ThirdConfigSnapshotPort configCache = mock(ThirdConfigSnapshotPort.class);
        ThirdProvider current = new ThirdProvider();
        current.setProviderId(7L);
        current.setProviderCode("qichacha");
        when(providerDao.findActiveById(7L)).thenReturn(current);

        ThirdProviderBo update = new ThirdProviderBo();
        update.setProviderId(7L);
        update.setProviderCode("other-provider");

        ThirdProviderService service = new ThirdProviderService(providerDao, configCache);

        assertThrows(ServiceException.class, () -> service.save(update));
        verify(providerDao, never()).update(current);
        verify(configCache, never()).evict("other-provider", null);
    }
}
