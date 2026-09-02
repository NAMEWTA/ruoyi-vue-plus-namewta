package org.dromara.profile.person.domain.material;

import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerKey;

public record MaterialOwner(MaterialOwnerKey key, Long applicantUserId) {
}
