package org.dromara.profile.shared.material;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.dromara.profile.api.domain.ProfileType;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialNodeCommand;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialNodeType;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerKey;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerType;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialScope;
import org.dromara.profile.shared.material.mapper.ProfileMaterialMapper;
import org.dromara.profile.shared.material.persistence.MaterialNodeRow;
import org.dromara.profile.shared.material.persistence.MaterialReferenceRow;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class MybatisProfileMaterialRepository implements ProfileMaterialRepository {

    private final ProfileMaterialMapper mapper;

    @Override
    public List<MaterialNode> nodes(MaterialScope scope, boolean includeDisabled) {
        return mapper.selectNodes(scope.name(), includeDisabled).stream().map(this::node).toList();
    }

    @Override
    public MaterialNode requireNode(Long materialNodeId) {
        return requireNode(mapper.selectNode(requirePositive(materialNodeId, "materialNodeId")));
    }

    @Override
    public MaterialNode requireNodeForUpdate(Long materialNodeId) {
        return requireNode(mapper.lockNode(requirePositive(materialNodeId, "materialNodeId")));
    }

    @Override
    public MaterialNode insertNode(Long materialNodeId, MaterialNodeCommand command, int depth) {
        try {
            mapper.insertNode(materialNodeId, command.parentId(), command.nodeType().name(), depth,
                command.scope().name(), command.materialTagCode(), command.nodeName().strip(),
                command.systemRequired() ? "Y" : "N", command.orderNum());
        } catch (DuplicateKeyException ex) {
            throw failure("MATERIAL_TAG_CODE_CONFLICT", ex);
        }
        return requireNode(materialNodeId);
    }

    @Override
    public MaterialNode updateNode(Long materialNodeId, MaterialNodeCommand command, int depth) {
        try {
            requireChanged(mapper.updateNode(materialNodeId, command.parentId(), depth, command.scope().name(),
                command.materialTagCode(), command.nodeName().strip(), command.systemRequired() ? "Y" : "N",
                command.orderNum(), command.expectedVersion()));
        } catch (DuplicateKeyException ex) {
            throw failure("MATERIAL_TAG_CODE_CONFLICT", ex);
        }
        return requireNode(materialNodeId);
    }

    @Override
    public long countChildren(Long materialNodeId) {
        return mapper.countChildren(materialNodeId);
    }

    @Override
    public long countReferences(Long materialNodeId) {
        return mapper.countReferences(materialNodeId);
    }

    @Override
    public void changeStatus(Long materialNodeId, boolean enabled, int expectedVersion) {
        requireChanged(mapper.updateStatus(materialNodeId, enabled ? "0" : "1", expectedVersion));
    }

    @Override
    public void archiveNode(Long materialNodeId, int expectedVersion) {
        requireChanged(mapper.archiveNode(materialNodeId, expectedVersion));
    }

    @Override
    public MaterialOwner lockOwner(MaterialOwnerKey owner) {
        if (owner.ownerType() == MaterialOwnerType.WORKING) {
            Long applicant = owner.profileType() == ProfileType.PERSON
                ? mapper.lockPersonApplication(owner.ownerId())
                : mapper.lockEnterpriseApplication(owner.ownerId());
            if (applicant == null) {
                throw failure("MATERIAL_OWNER_NOT_FOUND");
            }
            return new MaterialOwner(owner, applicant);
        }
        if (owner.ownerType() == MaterialOwnerType.SUBMISSION) {
            Long applicant = owner.profileType() == ProfileType.PERSON
                ? mapper.lockPersonSubmissionOwner(owner.ownerId())
                : mapper.lockEnterpriseSubmissionOwner(owner.ownerId());
            if (applicant == null) {
                throw failure("MATERIAL_OWNER_NOT_FOUND");
            }
            return new MaterialOwner(owner, applicant);
        }
        if (mapper.countImmutableOwner(owner.profileType().name(), owner.ownerType().name(), owner.ownerId()) != 1) {
            throw failure("MATERIAL_OWNER_NOT_FOUND");
        }
        return new MaterialOwner(owner, null);
    }

    @Override
    public long countAttached(MaterialOwnerKey owner) {
        return mapper.countAttached(owner.profileType().name(), owner.ownerType().name(), owner.ownerId());
    }

    @Override
    public MaterialReference insertReference(MaterialReference reference) {
        try {
            requireChanged(mapper.insertReference(row(reference)));
            return reference;
        } catch (DuplicateKeyException ex) {
            throw failure("MATERIAL_ALREADY_ATTACHED", ex);
        }
    }

    @Override
    public MaterialReference requireReference(Long materialRefId) {
        MaterialReferenceRow row = mapper.lockReference(requirePositive(materialRefId, "materialRefId"));
        if (row == null) {
            throw failure("MATERIAL_NOT_FOUND");
        }
        return reference(row);
    }

    @Override
    public void detach(Long materialRefId, Instant detachedTime) {
        requireChanged(mapper.detachReference(materialRefId, detachedTime));
    }

    @Override
    public List<MaterialReference> references(MaterialOwnerKey owner) {
        return mapper.selectReferences(owner.profileType().name(), owner.ownerType().name(), owner.ownerId())
            .stream().map(this::reference).toList();
    }

    @Override
    public List<MaterialRequirement> requirements(ProfileType profileType, String documentTypeCode,
                                                   Set<String> conditions) {
        return mapper.selectRequirements(profileType.name(), documentTypeCode, conditions).stream()
            .map(row -> new MaterialRequirement(row.materialTagCode(), row.minimumCount())).toList();
    }

    @Override
    public Map<String, Long> attachedCountsByTag(MaterialOwnerKey owner) {
        Map<String, Long> counts = new LinkedHashMap<>();
        mapper.selectAttachedCounts(owner.profileType().name(), owner.ownerType().name(), owner.ownerId())
            .forEach(row -> counts.put(row.materialTagCode(), row.materialCount()));
        return Map.copyOf(counts);
    }

    @Override
    public List<MaterialReference> insertImmutableCopies(MaterialOwnerKey source, MaterialOwnerKey target,
                                                          Instant attachedTime) {
        return references(source).stream().filter(MaterialReference::attached).map(existing -> {
            MaterialReference copy = new MaterialReference(IdWorker.getId(), target, existing.ossId(),
                existing.materialNodeId(), existing.materialTagCode(), existing.materialTagName(),
                existing.fileName(), existing.fileSize(), existing.fileExtension(), existing.mimeType(),
                true, true, attachedTime, null, 0);
            return insertReference(copy);
        }).toList();
    }

    private MaterialNode node(MaterialNodeRow row) {
        return new MaterialNode(row.materialNodeId(), row.parentId(), MaterialNodeType.valueOf(row.nodeType()),
            row.nodeDepth(), MaterialScope.valueOf(row.profileType()), row.materialTagCode(), row.nodeName(),
            "Y".equals(row.systemRequired()), "0".equals(row.status()), row.orderNum(), row.version());
    }

    private MaterialNode requireNode(MaterialNodeRow row) {
        if (row == null) {
            throw failure("MATERIAL_NODE_NOT_FOUND");
        }
        return node(row);
    }

    private MaterialReference reference(MaterialReferenceRow row) {
        MaterialOwnerKey owner = new MaterialOwnerKey(ProfileType.valueOf(row.profileType()),
            MaterialOwnerType.valueOf(row.ownerType()), row.ownerId());
        return new MaterialReference(row.materialRefId(), owner, row.ossId(), row.materialNodeId(),
            row.materialTagCode(), row.materialTagName(), row.fileName(), row.fileSize(), row.fileExtension(),
            row.mimeType(), "ATTACHED".equals(row.status()), "Y".equals(row.immutableFlag()),
            row.attachedTime(), row.detachedTime(), row.version());
    }

    private MaterialReferenceRow row(MaterialReference reference) {
        return new MaterialReferenceRow(reference.materialRefId(), reference.owner().ownerType().name(),
            reference.owner().ownerId(), reference.owner().profileType().name(), reference.ossId(),
            reference.materialNodeId(), reference.materialTagCode(), reference.materialTagName(),
            reference.fileName(), reference.fileSize(), reference.fileExtension(), reference.mimeType(),
            reference.attached() ? "ATTACHED" : "DETACHED", reference.immutableEvidence() ? "Y" : "N",
            reference.attachedTime(), reference.detachedTime(), reference.version());
    }

    private long requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw failure(field + "_INVALID");
        }
        return value;
    }

    private void requireChanged(int changed) {
        if (changed != 1) {
            throw failure("MATERIAL_VERSION_CONFLICT");
        }
    }

    private ProfileMaterialException failure(String category) {
        return new ProfileMaterialException(category);
    }

    private ProfileMaterialException failure(String category, Throwable cause) {
        ProfileMaterialException exception = new ProfileMaterialException(category);
        exception.initCause(cause);
        return exception;
    }
}
