package org.dromara.profile.shared.material;

import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerKey;

public record MaterialOwner(MaterialOwnerKey key, Long applicantUserId) {
}
