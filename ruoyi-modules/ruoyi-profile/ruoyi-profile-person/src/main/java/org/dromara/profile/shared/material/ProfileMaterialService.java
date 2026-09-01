package org.dromara.profile.shared.material;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import org.dromara.profile.api.domain.ProfileType;
import org.dromara.profile.api.material.ProfileMaterialPort;
import org.dromara.system.api.OssService;
import org.springframework.beans.factory.annotation.Autowired;
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

@Service
public class ProfileMaterialService implements ProfileMaterialPort {

    static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    static final int MAX_FILE_COUNT = 10;
    private static final String REFERENCE_TABLE = "profile_material_ref";
    private static final Map<String, Set<String>> ALLOWED_TYPES = Map.of(
        ".jpg", Set.of("image/jpeg"),
        ".jpeg", Set.of("image/jpeg"),
        ".png", Set.of("image/png"),
        ".pdf", Set.of("application/pdf"));

    private final ProfileMaterialRepository repository;
    private final OssService ossService;
    private final ProfileMaterialAccessPolicy accessPolicy;
    private final Clock clock;

    @Autowired
    public ProfileMaterialService(ProfileMaterialRepository repository, OssService ossService,
                                  ProfileMaterialAccessPolicy accessPolicy) {
        this(repository, ossService, accessPolicy, Clock.systemUTC());
    }

    ProfileMaterialService(ProfileMaterialRepository repository, OssService ossService,
                           ProfileMaterialAccessPolicy accessPolicy, Clock clock) {
        this.repository = repository;
        this.ossService = ossService;
        this.accessPolicy = accessPolicy;
        this.clock = clock;
    }

    @Override
    public List<MaterialNodeView> tree(MaterialScope scope, boolean includeDisabled) {
        Objects.requireNonNull(scope, "scope");
        if (includeDisabled) {
            accessPolicy.requireCatalogRead();
        }
        return buildTree(repository.nodes(scope, includeDisabled));
    }

    @Override
    @DSTransactional
    public MaterialNodeView createNode(MaterialNodeCommand command) {
        accessPolicy.requireCatalogManage();
        Shape shape = shape(command, null);
        return view(repository.insertNode(IdWorker.getId(), command, shape.depth()), List.of());
    }

    @Override
    @DSTransactional
    public MaterialNodeView updateNode(Long materialNodeId, MaterialNodeCommand command) {
        accessPolicy.requireCatalogManage();
        MaterialNode current = repository.requireNodeForUpdate(materialNodeId);
        protectSystemCode(current, command);
        Shape shape = shape(command, current);
        return view(repository.updateNode(materialNodeId, command, shape.depth()), List.of());
    }

    @Override
    @DSTransactional
    public void changeStatus(Long materialNodeId, boolean enabled, int expectedVersion) {
        accessPolicy.requireCatalogManage();
        MaterialNode current = repository.requireNodeForUpdate(materialNodeId);
        if (!enabled && (current.systemRequired() || current.nodeDepth() == 1)) {
            throw failure("SYSTEM_MATERIAL_TAG_PROTECTED");
        }
        if (!enabled && current.nodeType() == MaterialNodeType.CATEGORY && repository.countChildren(materialNodeId) > 0) {
            throw failure("MATERIAL_CATEGORY_NOT_EMPTY");
        }
        repository.changeStatus(materialNodeId, enabled, expectedVersion);
    }

    @Override
    @DSTransactional
    public void archiveNode(Long materialNodeId, int expectedVersion) {
        accessPolicy.requireCatalogManage();
        MaterialNode current = repository.requireNodeForUpdate(materialNodeId);
        if (current.systemRequired() || current.nodeDepth() == 1) {
            throw failure("SYSTEM_MATERIAL_TAG_PROTECTED");
        }
        if (repository.countChildren(materialNodeId) > 0 || repository.countReferences(materialNodeId) > 0) {
            throw failure("MATERIAL_NODE_IN_USE");
        }
        repository.archiveNode(materialNodeId, expectedVersion);
    }

    @Override
    @DSTransactional
    public MaterialReferenceView attach(MaterialAttachCommand command) {
        MaterialOwner owner = repository.lockOwner(command.owner());
        accessPolicy.requireWrite(owner);
        if (command.owner().ownerType() != MaterialOwnerType.WORKING) {
            throw failure("IMMUTABLE_MATERIAL_OWNER");
        }
        MaterialNode tag = repository.requireNode(command.materialNodeId());
        requireApplicableTag(command.owner().profileType(), tag);
        if (repository.countAttached(command.owner()) >= MAX_FILE_COUNT) {
            throw failure("MATERIAL_COUNT_LIMIT");
        }
        OssService.OssObjectMetadata metadata = ossService.objectMetadata(command.ossId());
        if (!Objects.equals(owner.applicantUserId(), metadata.uploaderUserId())) {
            throw failure("MATERIAL_OSS_OWNER_MISMATCH");
        }
        String extension = validateMetadata(metadata);
        Instant now = clock.instant();
        MaterialReference reference = new MaterialReference(IdWorker.getId(), command.owner(), command.ossId(),
            tag.materialNodeId(), tag.materialTagCode(), tag.nodeName(), metadata.fileName(), metadata.fileSize(),
            extension, normalizeMime(metadata.contentType()), true, false, now, null, 0);
        MaterialReference inserted = repository.insertReference(reference);
        ossService.reconcileReferences(REFERENCE_TABLE, String.valueOf(inserted.materialRefId()),
            Set.of(), Set.of(inserted.ossId()));
        return view(inserted);
    }

    @Override
    @DSTransactional
    public void detach(MaterialOwnerKey ownerKey, Long materialRefId) {
        MaterialOwner owner = repository.lockOwner(ownerKey);
        accessPolicy.requireWrite(owner);
        MaterialReference reference = repository.requireReference(materialRefId);
        requireSameOwner(ownerKey, reference);
        if (ownerKey.ownerType() != MaterialOwnerType.WORKING || reference.immutableEvidence()) {
            throw failure("IMMUTABLE_MATERIAL");
        }
        if (!reference.attached()) {
            return;
        }
        repository.detach(materialRefId, clock.instant());
        ossService.reconcileReferences(REFERENCE_TABLE, String.valueOf(materialRefId),
            Set.of(reference.ossId()), Set.of());
    }

    @Override
    public List<MaterialReferenceView> list(MaterialOwnerKey ownerKey) {
        MaterialOwner owner = repository.lockOwner(ownerKey);
        accessPolicy.requireRead(owner);
        return repository.references(ownerKey).stream().map(this::view).toList();
    }

    @Override
    public OssService.OssAccessUrl accessUrl(MaterialOwnerKey ownerKey, Long materialRefId) {
        MaterialOwner owner = repository.lockOwner(ownerKey);
        accessPolicy.requireRead(owner);
        MaterialReference reference = repository.requireReference(materialRefId);
        requireSameOwner(ownerKey, reference);
        if (!reference.attached() && ownerKey.ownerType() == MaterialOwnerType.WORKING) {
            throw failure("MATERIAL_NOT_ATTACHED");
        }
        return ossService.resolveAccessUrl(reference.ossId());
    }

    @Override
    public void validateRequired(MaterialOwnerKey ownerKey, String documentTypeCode, Set<String> conditions) {
        MaterialOwner owner = repository.lockOwner(ownerKey);
        accessPolicy.requireRead(owner);
        Set<String> normalizedConditions = conditions == null || conditions.isEmpty()
            ? Set.of("ALWAYS") : Set.copyOf(conditions);
        Map<String, Long> counts = repository.attachedCountsByTag(ownerKey);
        for (MaterialRequirement requirement : repository.requirements(
            ownerKey.profileType(), requireText(documentTypeCode, "documentTypeCode"), normalizedConditions)) {
            if (counts.getOrDefault(requirement.materialTagCode(), 0L) < requirement.minimumCount()) {
                throw failure("MISSING_REQUIRED_MATERIAL:" + requirement.materialTagCode());
            }
        }
    }

    @Override
    @DSTransactional
    public List<MaterialReferenceView> snapshotImmutable(MaterialOwnerKey source, MaterialOwnerKey target) {
        if (source.profileType() != target.profileType() || source.ownerType() != MaterialOwnerType.WORKING
            || target.ownerType() == MaterialOwnerType.WORKING) {
            throw failure("MATERIAL_SNAPSHOT_OWNER_INVALID");
        }
        MaterialOwner sourceOwner = repository.lockOwner(source);
        accessPolicy.requireWrite(sourceOwner);
        MaterialOwner targetOwner = repository.lockOwner(target);
        if (targetOwner.applicantUserId() != null
            && !Objects.equals(sourceOwner.applicantUserId(), targetOwner.applicantUserId())) {
            throw failure("MATERIAL_SNAPSHOT_OWNER_INVALID");
        }
        return repository.insertImmutableCopies(source, target, clock.instant()).stream().map(reference -> {
            ossService.reconcileReferences(REFERENCE_TABLE, String.valueOf(reference.materialRefId()),
                Set.of(), Set.of(reference.ossId()));
            return view(reference);
        }).toList();
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
        MaterialNode parent = repository.requireNode(command.parentId());
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

    private record Shape(int depth) {
    }
}
