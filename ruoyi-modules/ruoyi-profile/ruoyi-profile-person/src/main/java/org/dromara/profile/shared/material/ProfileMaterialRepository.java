package org.dromara.profile.shared.material;

import org.dromara.profile.api.domain.ProfileType;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialNodeCommand;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerKey;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialScope;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ProfileMaterialRepository {

    List<MaterialNode> nodes(MaterialScope scope, boolean includeDisabled);

    MaterialNode requireNode(Long materialNodeId);

    MaterialNode requireNodeForUpdate(Long materialNodeId);

    MaterialNode insertNode(Long materialNodeId, MaterialNodeCommand command, int depth);

    MaterialNode updateNode(Long materialNodeId, MaterialNodeCommand command, int depth);

    long countChildren(Long materialNodeId);

    long countReferences(Long materialNodeId);

    void changeStatus(Long materialNodeId, boolean enabled, int expectedVersion);

    void archiveNode(Long materialNodeId, int expectedVersion);

    MaterialOwner lockOwner(MaterialOwnerKey owner);

    long countAttached(MaterialOwnerKey owner);

    MaterialReference insertReference(MaterialReference reference);

    MaterialReference requireReference(Long materialRefId);

    void detach(Long materialRefId, Instant detachedTime);

    List<MaterialReference> references(MaterialOwnerKey owner);

    List<MaterialRequirement> requirements(ProfileType profileType, String documentTypeCode, Set<String> conditions);

    Map<String, Long> attachedCountsByTag(MaterialOwnerKey owner);

    List<MaterialReference> insertImmutableCopies(MaterialOwnerKey source, MaterialOwnerKey target, Instant attachedTime);
}
