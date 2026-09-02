package org.dromara.profile.person.service.impl;

import org.dromara.profile.person.domain.exception.ProfileMaterialException;
import org.dromara.profile.person.domain.material.MaterialNode;
import org.dromara.profile.person.domain.material.MaterialOwner;
import org.dromara.profile.person.domain.material.MaterialReference;
import org.dromara.profile.person.domain.material.MaterialRequirement;
import org.dromara.profile.person.domain.vo.MaterialNodeRow;
import org.dromara.profile.person.domain.vo.MaterialReferenceRow;
import org.dromara.profile.person.mapper.ProfileMaterialMapper;
import org.dromara.profile.person.service.IProfileMaterialService;
import org.dromara.profile.person.service.ProfileMaterialAccessPolicy;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import org.dromara.profile.api.domain.ProfileType;
import org.dromara.profile.api.material.ProfileMaterialOwnerContributor;
import org.dromara.profile.api.material.ProfileMaterialOwnerContributor.ResolvedMaterialOwner;
import org.dromara.profile.api.material.ProfileMaterialOwnerContributor.SnapshotRelationship;
import org.dromara.system.api.OssService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProfileMaterialServiceImpl implements IProfileMaterialService {

    static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    static final int MAX_FILE_COUNT = 10;
    private static final String REFERENCE_TABLE = "profile_material_ref";
    private static final Map<String, Set<String>> ALLOWED_TYPES = Map.of(
        ".jpg", Set.of("image/jpeg"),
        ".jpeg", Set.of("image/jpeg"),
        ".png", Set.of("image/png"),
        ".pdf", Set.of("application/pdf"));

    private final ProfileMaterialMapper mapper;
    private final OssService ossService;
    private final ProfileMaterialAccessPolicy accessPolicy;
    private final Map<ProfileType, ProfileMaterialOwnerContributor> ownerContributors;
    private final Clock clock;

    @Autowired
    public ProfileMaterialServiceImpl(ProfileMaterialMapper mapper, OssService ossService,
                                      ProfileMaterialAccessPolicy accessPolicy,
                                      List<ProfileMaterialOwnerContributor> ownerContributors) {
        this(mapper, ossService, accessPolicy, ownerContributors, Clock.systemUTC());
    }

    ProfileMaterialServiceImpl(ProfileMaterialMapper mapper, OssService ossService,
                               ProfileMaterialAccessPolicy accessPolicy,
                               List<ProfileMaterialOwnerContributor> ownerContributors,
                               Clock clock) {
        this.mapper = mapper;
        this.ossService = ossService;
        this.accessPolicy = accessPolicy;
        this.ownerContributors = ownerContributors.stream().collect(Collectors.toUnmodifiableMap(
            ProfileMaterialOwnerContributor::profileType, Function.identity()));
        this.clock = clock;
    }

    @Override
    public List<MaterialNodeView> tree(MaterialScope scope, boolean includeDisabled) {
        Objects.requireNonNull(scope, "scope");
        if (includeDisabled) {
            accessPolicy.requireCatalogRead();
        }
        return buildTree(nodes(scope, includeDisabled));
    }

    @Override
    @DSTransactional
    public MaterialNodeView createNode(MaterialNodeCommand command) {
        accessPolicy.requireCatalogManage();
        Shape shape = shape(command, null);
        return view(insertNode(IdWorker.getId(), command, shape.depth()), List.of());
    }

    @Override
    @DSTransactional
    public MaterialNodeView updateNode(Long materialNodeId, MaterialNodeCommand command) {
        accessPolicy.requireCatalogManage();
        MaterialNode current = requireNodeForUpdate(materialNodeId);
        protectSystemCode(current, command);
        Shape shape = shape(command, current);
        return view(updateNode(materialNodeId, command, shape.depth()), List.of());
    }

    @Override
    @DSTransactional
    public void changeStatus(Long materialNodeId, boolean enabled, int expectedVersion) {
        accessPolicy.requireCatalogManage();
        MaterialNode current = requireNodeForUpdate(materialNodeId);
        if (!enabled && (current.systemRequired() || current.nodeDepth() == 1)) {
            throw failure("SYSTEM_MATERIAL_TAG_PROTECTED");
        }
        if (!enabled && current.nodeType() == MaterialNodeType.CATEGORY && countChildren(materialNodeId) > 0) {
            throw failure("MATERIAL_CATEGORY_NOT_EMPTY");
        }
        changeNodeStatus(materialNodeId, enabled, expectedVersion);
    }

    @Override
    @DSTransactional
    public void archiveNode(Long materialNodeId, int expectedVersion) {
        accessPolicy.requireCatalogManage();
        MaterialNode current = requireNodeForUpdate(materialNodeId);
        if (current.systemRequired() || current.nodeDepth() == 1) {
            throw failure("SYSTEM_MATERIAL_TAG_PROTECTED");
        }
        if (countChildren(materialNodeId) > 0 || countReferences(materialNodeId) > 0) {
            throw failure("MATERIAL_NODE_IN_USE");
        }
        archiveMaterialNode(materialNodeId, expectedVersion);
    }

    @Override
    @DSTransactional
    public MaterialReferenceView attach(MaterialAttachCommand command) {
        MaterialOwner owner = lockOwner(command.owner());
        MaterialOwnerType ownerType = command.owner().ownerType();
        if (ownerType != MaterialOwnerType.WORKING && ownerType != MaterialOwnerType.SOURCE) {
            throw failure("IMMUTABLE_MATERIAL_OWNER");
        }
        Long authorizedUploaderUserId = accessPolicy.requireAttach(owner);
        if (ownerType == MaterialOwnerType.WORKING) {
            requireWorkingEditable(command.owner());
        }
        MaterialNode tag = requireNode(command.materialNodeId());
        requireApplicableTag(command.owner().profileType(), tag);
        if (countAttached(command.owner()) >= MAX_FILE_COUNT) {
            throw failure("MATERIAL_COUNT_LIMIT");
        }
        OssService.OssObjectMetadata metadata = ossService.objectMetadata(command.ossId());
        if (!Objects.equals(authorizedUploaderUserId, metadata.uploaderUserId())) {
            throw failure("MATERIAL_OSS_OWNER_MISMATCH");
        }
        String extension = validateMetadata(metadata);
        Instant now = clock.instant();
        MaterialReference reference = new MaterialReference(IdWorker.getId(), command.owner(), command.ossId(),
            tag.materialNodeId(), tag.materialTagCode(), tag.nodeName(), metadata.fileName(), metadata.fileSize(),
            extension, normalizeMime(metadata.contentType()), true,
            ownerType == MaterialOwnerType.SOURCE, now, null, 0);
        MaterialReference inserted = insertReference(reference);
        ossService.reconcileReferences(REFERENCE_TABLE, String.valueOf(inserted.materialRefId()),
            Set.of(), Set.of(inserted.ossId()));
        return view(inserted);
    }

    @Override
    @DSTransactional
    public void detach(MaterialOwnerKey ownerKey, Long materialRefId) {
        MaterialOwner owner = lockOwner(ownerKey);
        accessPolicy.requireWrite(owner);
        if (ownerKey.ownerType() != MaterialOwnerType.WORKING) {
            throw failure("IMMUTABLE_MATERIAL");
        }
        requireWorkingEditable(ownerKey);
        MaterialReference reference = requireReference(materialRefId);
        requireSameOwner(ownerKey, reference);
        if (reference.immutableEvidence()) {
            throw failure("IMMUTABLE_MATERIAL");
        }
        if (!reference.attached()) {
            return;
        }
        detachReference(materialRefId, clock.instant());
        ossService.reconcileReferences(REFERENCE_TABLE, String.valueOf(materialRefId),
            Set.of(reference.ossId()), Set.of());
    }

    @Override
    @DSTransactional
    public List<MaterialReferenceView> list(MaterialOwnerKey ownerKey) {
        MaterialOwner owner = lockOwner(ownerKey);
        accessPolicy.requireRead(owner);
        return references(ownerKey).stream().map(this::view).toList();
    }

    @Override
    @DSTransactional
    public OssService.OssAccessUrl accessUrl(MaterialOwnerKey ownerKey, Long materialRefId) {
        MaterialOwner owner = lockOwner(ownerKey);
        accessPolicy.requireRead(owner);
        MaterialReference reference = requireReference(materialRefId);
        requireSameOwner(ownerKey, reference);
        if (!reference.attached() && ownerKey.ownerType() == MaterialOwnerType.WORKING) {
            throw failure("MATERIAL_NOT_ATTACHED");
        }
        return ossService.resolveAccessUrl(reference.ossId());
    }

    @Override
    @DSTransactional
    public void validateRequired(MaterialOwnerKey ownerKey, String documentTypeCode, Set<String> conditions) {
        MaterialOwner owner = lockOwner(ownerKey);
        accessPolicy.requireRead(owner);
        Set<String> normalizedConditions = conditions == null || conditions.isEmpty()
            ? Set.of("ALWAYS") : Set.copyOf(conditions);
        Map<String, Long> counts = attachedCountsByTag(ownerKey);
        for (MaterialRequirement requirement : requirements(
            ownerKey.profileType(), requireText(documentTypeCode, "documentTypeCode"), normalizedConditions)) {
            if (counts.getOrDefault(requirement.materialTagCode(), 0L) < requirement.minimumCount()) {
                throw failure("MISSING_REQUIRED_MATERIAL:" + requirement.materialTagCode());
            }
        }
    }

    @Override
    @DSTransactional
    public List<MaterialReferenceView> snapshotImmutable(MaterialOwnerKey source, MaterialOwnerKey target) {
        if (source.profileType() != target.profileType() || !validSnapshotTransition(source, target)) {
            throw failure("MATERIAL_SNAPSHOT_OWNER_INVALID");
        }
        MaterialOwner sourceOwner = lockOwner(source);
        if (source.ownerType() == MaterialOwnerType.WORKING) {
            requireWorkingEditable(source);
            accessPolicy.requireWrite(sourceOwner);
        }
        MaterialOwner targetOwner = lockOwner(target);
        if (targetOwner.applicantUserId() != null
            && !Objects.equals(sourceOwner.applicantUserId(), targetOwner.applicantUserId())) {
            throw failure("MATERIAL_SNAPSHOT_OWNER_INVALID");
        }
        if (!ownerContributor(source.profileType()).hasSnapshotRelationship(
            new SnapshotRelationship(source, target))) {
            throw failure("MATERIAL_SNAPSHOT_OWNER_INVALID");
        }
        return insertImmutableCopies(source, target, clock.instant()).stream().map(reference -> {
            ossService.reconcileReferences(REFERENCE_TABLE, String.valueOf(reference.materialRefId()),
                Set.of(), Set.of(reference.ossId()));
            return view(reference);
        }).toList();
    }

    private boolean validSnapshotTransition(MaterialOwnerKey source, MaterialOwnerKey target) {
        return (source.ownerType() == MaterialOwnerType.WORKING
            && target.ownerType() == MaterialOwnerType.SUBMISSION)
            || (source.ownerType() == MaterialOwnerType.SUBMISSION
            && target.ownerType() == MaterialOwnerType.VERSION)
            || (source.ownerType() == MaterialOwnerType.SOURCE
            && target.ownerType() == MaterialOwnerType.VERSION);
    }

    private MaterialOwner lockOwner(MaterialOwnerKey owner) {
        ResolvedMaterialOwner resolved = ownerContributor(owner.profileType()).lockOwner(owner)
            .orElseThrow(() -> failure("MATERIAL_OWNER_NOT_FOUND"));
        return new MaterialOwner(resolved.owner(), resolved.applicantUserId());
    }

    private void requireWorkingEditable(MaterialOwnerKey owner) {
        if (!ownerContributor(owner.profileType()).isWorkingEditable(owner)) {
            throw failure("MATERIAL_OWNER_READ_ONLY");
        }
    }

    private ProfileMaterialOwnerContributor ownerContributor(ProfileType profileType) {
        ProfileMaterialOwnerContributor contributor = ownerContributors.get(profileType);
        if (contributor == null) {
            throw failure("MATERIAL_OWNER_NOT_FOUND");
        }
        return contributor;
    }

    private Shape shape(MaterialNodeCommand command, MaterialNode current) {
        String nodeName = requireText(command.nodeName(), "nodeName");
        if (current == null && command.systemRequired()) {
            throw failure("SYSTEM_MATERIAL_TAG_PROTECTED");
        }
        if (nodeName.length() > 100 || command.parentId() < 0 || command.orderNum() < 0) {
            throw failure("MATERIAL_NODE_INVALID");
        }
        if (command.parentId() == 0) {
            if (current == null) {
                throw failure("MATERIAL_ROOT_SCOPE_FIXED");
            }
            if (command.nodeType() != MaterialNodeType.CATEGORY || command.materialTagCode() != null
                || command.systemRequired()) {
                throw failure("MATERIAL_TREE_SHAPE_INVALID");
            }
            return new Shape(1);
        }
        MaterialNode parent = requireNode(command.parentId());
        if (parent.nodeType() != MaterialNodeType.CATEGORY || !parent.enabled()) {
            throw failure("MATERIAL_TREE_PARENT_INVALID");
        }
        if (parent.scope() != command.scope()) {
            throw failure("MATERIAL_TREE_PROFILE_MISMATCH");
        }
        int depth = parent.nodeDepth() + 1;
        if (command.nodeType() == MaterialNodeType.CATEGORY) {
            if (depth != 2 || command.materialTagCode() != null || command.systemRequired()) {
                throw failure("MATERIAL_TREE_SHAPE_INVALID");
            }
        } else if ((depth != 2 && depth != 3) || command.materialTagCode() == null
            || !command.materialTagCode().matches("[A-Z][A-Z0-9_]{1,63}")) {
            throw failure("MATERIAL_TREE_SHAPE_INVALID");
        }
        if (current != null && current.nodeType() != command.nodeType()) {
            throw failure("MATERIAL_NODE_TYPE_IMMUTABLE");
        }
        return new Shape(depth);
    }

    private void protectSystemCode(MaterialNode current, MaterialNodeCommand command) {
        if (current.scope() != command.scope()) {
            throw failure("MATERIAL_NODE_SCOPE_IMMUTABLE");
        }
        if (current.nodeType() == MaterialNodeType.TAG
            && !Objects.equals(current.materialTagCode(), command.materialTagCode())) {
            throw failure(current.systemRequired()
                ? "SYSTEM_MATERIAL_TAG_PROTECTED" : "MATERIAL_TAG_CODE_IMMUTABLE");
        }
        if (current.systemRequired() && !command.systemRequired()) {
            throw failure("SYSTEM_MATERIAL_TAG_PROTECTED");
        }
    }

    private void requireApplicableTag(ProfileType ownerType, MaterialNode tag) {
        if (tag.nodeType() != MaterialNodeType.TAG || !tag.enabled()) {
            throw failure("MATERIAL_TAG_UNAVAILABLE");
        }
        if (tag.scope() != MaterialScope.COMMON && !tag.scope().name().equals(ownerType.name())) {
            throw failure("MATERIAL_TAG_NOT_APPLICABLE");
        }
    }

    private String validateMetadata(OssService.OssObjectMetadata metadata) {
        if (metadata.fileSize() <= 0 || metadata.fileSize() > MAX_FILE_SIZE) {
            throw failure("MATERIAL_FILE_TOO_LARGE");
        }
        String originalExtension = extension(metadata.fileName());
        String storedExtension = normalizeExtension(metadata.fileSuffix());
        String mime = normalizeMime(metadata.contentType());
        if (!originalExtension.equals(storedExtension)
            || !ALLOWED_TYPES.getOrDefault(originalExtension, Set.of()).contains(mime)) {
            throw failure("MATERIAL_FILE_TYPE_INVALID");
        }
        return originalExtension;
    }

    private String extension(String fileName) {
        String value = requireText(fileName, "fileName");
        int index = value.lastIndexOf('.');
        return index < 0 ? "" : normalizeExtension(value.substring(index));
    }

    private String normalizeExtension(String extension) {
        String value = requireText(extension, "fileSuffix").toLowerCase(Locale.ROOT);
        return value.startsWith(".") ? value : "." + value;
    }

    private String normalizeMime(String mime) {
        return requireText(mime, "contentType").toLowerCase(Locale.ROOT);
    }

    private List<MaterialNode> nodes(MaterialScope scope, boolean includeDisabled) {
        return mapper.selectNodes(scope.name(), includeDisabled).stream().map(this::node).toList();
    }

    private MaterialNode requireNode(Long materialNodeId) {
        return requireNode(mapper.selectNode(requirePositive(materialNodeId, "materialNodeId")));
    }

    private MaterialNode requireNodeForUpdate(Long materialNodeId) {
        return requireNode(mapper.lockNode(requirePositive(materialNodeId, "materialNodeId")));
    }

    private MaterialNode insertNode(Long materialNodeId, MaterialNodeCommand command, int depth) {
        try {
            mapper.insertNode(materialNodeId, command.parentId(), command.nodeType().name(), depth,
                command.scope().name(), command.materialTagCode(), command.nodeName().strip(),
                command.systemRequired() ? "Y" : "N", command.orderNum());
        } catch (DuplicateKeyException exception) {
            throw failure("MATERIAL_TAG_CODE_CONFLICT", exception);
        }
        return requireNode(materialNodeId);
    }

    private MaterialNode updateNode(Long materialNodeId, MaterialNodeCommand command, int depth) {
        try {
            requireChanged(mapper.updateNode(materialNodeId, command.parentId(), depth, command.scope().name(),
                command.materialTagCode(), command.nodeName().strip(), command.systemRequired() ? "Y" : "N",
                command.orderNum(), command.expectedVersion()));
        } catch (DuplicateKeyException exception) {
            throw failure("MATERIAL_TAG_CODE_CONFLICT", exception);
        }
        return requireNode(materialNodeId);
    }

    private long countChildren(Long materialNodeId) {
        return mapper.countChildren(materialNodeId);
    }

    private long countReferences(Long materialNodeId) {
        return mapper.countReferences(materialNodeId);
    }

    private void changeNodeStatus(Long materialNodeId, boolean enabled, int expectedVersion) {
        requireChanged(mapper.updateStatus(materialNodeId, enabled ? "0" : "1", expectedVersion));
    }

    private void archiveMaterialNode(Long materialNodeId, int expectedVersion) {
        requireChanged(mapper.archiveNode(materialNodeId, expectedVersion));
    }

    private long countAttached(MaterialOwnerKey owner) {
        return mapper.countAttached(owner.profileType().name(), owner.ownerType().name(), owner.ownerId());
    }

    private MaterialReference insertReference(MaterialReference reference) {
        try {
            requireChanged(mapper.insertReference(row(reference)));
            return reference;
        } catch (DuplicateKeyException exception) {
            throw failure("MATERIAL_ALREADY_ATTACHED", exception);
        }
    }

    private MaterialReference requireReference(Long materialRefId) {
        MaterialReferenceRow row = mapper.lockReference(requirePositive(materialRefId, "materialRefId"));
        if (row == null) {
            throw failure("MATERIAL_NOT_FOUND");
        }
        return reference(row);
    }

    private void detachReference(Long materialRefId, Instant detachedTime) {
        requireChanged(mapper.detachReference(materialRefId, detachedTime));
    }

    private List<MaterialReference> references(MaterialOwnerKey owner) {
        return mapper.selectReferences(owner.profileType().name(), owner.ownerType().name(), owner.ownerId())
            .stream().map(this::reference).toList();
    }

    private List<MaterialRequirement> requirements(ProfileType profileType, String documentTypeCode,
                                                    Set<String> conditions) {
        return mapper.selectRequirements(profileType.name(), documentTypeCode, conditions).stream()
            .map(row -> new MaterialRequirement(row.materialTagCode(), row.minimumCount())).toList();
    }

    private Map<String, Long> attachedCountsByTag(MaterialOwnerKey owner) {
        Map<String, Long> counts = new LinkedHashMap<>();
        mapper.selectAttachedCounts(owner.profileType().name(), owner.ownerType().name(), owner.ownerId())
            .forEach(row -> counts.put(row.materialTagCode(), row.materialCount()));
        return Map.copyOf(counts);
    }

    private List<MaterialReference> insertImmutableCopies(MaterialOwnerKey source, MaterialOwnerKey target,
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

    private List<MaterialNodeView> buildTree(List<MaterialNode> nodes) {
        Map<Long, List<MaterialNode>> children = new HashMap<>();
        nodes.forEach(node -> children.computeIfAbsent(node.parentId(), ignored -> new ArrayList<>()).add(node));
        Comparator<MaterialNode> order = Comparator.comparingInt(MaterialNode::orderNum)
            .thenComparing(MaterialNode::materialNodeId);
        children.values().forEach(list -> list.sort(order));
        return children.getOrDefault(0L, List.of()).stream().map(node -> treeView(node, children)).toList();
    }

    private MaterialNodeView treeView(MaterialNode node, Map<Long, List<MaterialNode>> children) {
        return view(node, children.getOrDefault(node.materialNodeId(), List.of()).stream()
            .map(child -> treeView(child, children)).toList());
    }

    private MaterialNodeView view(MaterialNode node, List<MaterialNodeView> children) {
        return new MaterialNodeView(node.materialNodeId(), node.parentId(), node.nodeType(), node.nodeDepth(),
            node.scope(), node.materialTagCode(), node.nodeName(), node.systemRequired(), node.enabled(),
            node.orderNum(), node.version(), children);
    }

    private MaterialReferenceView view(MaterialReference reference) {
        return new MaterialReferenceView(reference.materialRefId(), reference.owner(), reference.ossId(),
            reference.materialNodeId(), reference.materialTagCode(), reference.materialTagName(), reference.fileName(),
            reference.fileSize(), reference.fileExtension(), reference.mimeType(), reference.attached(),
            reference.immutableEvidence(), reference.attachedTime(), reference.detachedTime(), reference.version());
    }

    private void requireSameOwner(MaterialOwnerKey owner, MaterialReference reference) {
        if (!owner.equals(reference.owner())) {
            throw failure("MATERIAL_NOT_FOUND");
        }
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw failure("MATERIAL_" + field.toUpperCase(Locale.ROOT) + "_INVALID");
        }
        return value.strip();
    }

    private ProfileMaterialException failure(String category) {
        return new ProfileMaterialException(category);
    }

    private ProfileMaterialException failure(String category, Throwable cause) {
        ProfileMaterialException exception = new ProfileMaterialException(category);
        exception.initCause(cause);
        return exception;
    }

    private record Shape(int depth) {
    }
}
