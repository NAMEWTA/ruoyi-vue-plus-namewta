package org.dromara.third.port;

import org.dromara.third.domain.ThirdCredential;

import java.util.List;

/** Read boundary for effective provider and endpoint credentials. */
public interface ThirdCredentialStore {
    List<ThirdCredential> findByScopes(Long providerId, Long endpointId);
}
