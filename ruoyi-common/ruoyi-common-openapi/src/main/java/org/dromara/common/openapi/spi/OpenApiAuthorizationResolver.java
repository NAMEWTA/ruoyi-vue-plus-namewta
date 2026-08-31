package org.dromara.common.openapi.spi;

import org.dromara.system.api.model.LoginUser;

/**
 * Builds the current read-only global authorization snapshot for one user.
 */
@FunctionalInterface
public interface OpenApiAuthorizationResolver {

    LoginUser resolve(Long userId);

}
