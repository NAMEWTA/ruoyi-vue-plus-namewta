package org.dromara.profile.person.domain.material;

import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerKey;

/** MaterialOwner 材料领域模型。 */
public record MaterialOwner(MaterialOwnerKey key, Long applicantUserId) {
}
