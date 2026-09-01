package org.dromara.profile.shared.material;

import org.dromara.profile.api.domain.ProfileType;
import org.dromara.profile.api.material.ProfileMaterialPort;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialAttachCommand;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialNodeCommand;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialNodeType;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerKey;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerType;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialScope;
import org.dromara.system.api.OssService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class ProfileMaterialServiceTest {

    private final ProfileMaterialRepository repository = mock(ProfileMaterialRepository.class);
    private final OssService ossService = mock(OssService.class);
    private final ProfileMaterialAccessPolicy accessPolicy = mock(ProfileMaterialAccessPolicy.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC);
    private ProfileMaterialService service;

    @BeforeEach
    void setUp() {
        service = new ProfileMaterialService(repository, ossService, accessPolicy, clock);
    }

    @Test
    void attachesOneValidatedFileAndReconcilesTheNewReference() {
        MaterialOwnerKey owner = new MaterialOwnerKey(ProfileType.PERSON, MaterialOwnerType.WORKING, 91L);
        when(repository.lockOwner(owner)).thenReturn(new MaterialOwner(owner, 41L));
        when(repository.requireNode(501L)).thenReturn(tag(501L, ProfileType.PERSON, "ID_FRONT", false));
        when(repository.countAttached(owner)).thenReturn(9L);
        when(ossService.objectMetadata(71L)).thenReturn(new OssService.OssObjectMetadata(
            71L, "profile/front.jpg", "front.jpg", ".jpg", 10L * 1024 * 1024, "image/jpeg", 41L));
        when(repository.insertReference(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.attach(new MaterialAttachCommand(owner, 71L, 501L));

        assertThat(result.ossId()).isEqualTo(71L);
        assertThat(result.materialTagCode()).isEqualTo("ID_FRONT");
        verify(accessPolicy).requireWrite(new MaterialOwner(owner, 41L));
        verify(ossService).reconcileReferences("profile_material_ref", String.valueOf(result.materialRefId()),
            Set.of(), Set.of(71L));
    }

    @Test
    void rejectsCountSizeExtensionMimeAndTagFailuresBeforeWriting() {
        MaterialOwnerKey owner = new MaterialOwnerKey(ProfileType.PERSON, MaterialOwnerType.WORKING, 92L);
        when(repository.lockOwner(owner)).thenReturn(new MaterialOwner(owner, 42L));
        when(repository.requireNode(502L)).thenReturn(tag(502L, ProfileType.ENTERPRISE, "LICENSE", false));
        when(repository.countAttached(owner)).thenReturn(10L);

        assertThatThrownBy(() -> service.attach(new MaterialAttachCommand(owner, 72L, 502L)))
            .isInstanceOf(ProfileMaterialException.class)
            .hasMessageContaining("MATERIAL_TAG_NOT_APPLICABLE");

        when(repository.requireNode(502L)).thenReturn(tag(502L, ProfileType.PERSON, "ID_FRONT", false));
        assertThatThrownBy(() -> service.attach(new MaterialAttachCommand(owner, 72L, 502L)))
            .hasMessageContaining("MATERIAL_COUNT_LIMIT");

        when(repository.countAttached(owner)).thenReturn(0L);
        when(ossService.objectMetadata(72L)).thenReturn(new OssService.OssObjectMetadata(
            72L, "profile/front.png", "front.png", ".png", 1024, "image/png", 999L));
        assertThatThrownBy(() -> service.attach(new MaterialAttachCommand(owner, 72L, 502L)))
            .hasMessageContaining("MATERIAL_OSS_OWNER_MISMATCH");

        when(ossService.objectMetadata(72L)).thenReturn(new OssService.OssObjectMetadata(
            72L, "profile/front.png", "front.png", ".png", 10L * 1024 * 1024 + 1, "image/png", 42L));
        assertThatThrownBy(() -> service.attach(new MaterialAttachCommand(owner, 72L, 502L)))
            .hasMessageContaining("MATERIAL_FILE_TOO_LARGE");

        when(ossService.objectMetadata(72L)).thenReturn(new OssService.OssObjectMetadata(
            72L, "profile/front.exe", "front.exe", ".exe", 10, "application/pdf", 42L));
        assertThatThrownBy(() -> service.attach(new MaterialAttachCommand(owner, 72L, 502L)))
            .hasMessageContaining("MATERIAL_FILE_TYPE_INVALID");

        verify(repository, never()).insertReference(any());
        verify(ossService, never()).reconcileReferences(any(), any(), any(), any());
    }

    @Test
    void detachesOnlyMutableWorkingEvidenceAndNeverDeletesTheObject() {
        MaterialOwnerKey owner = new MaterialOwnerKey(ProfileType.PERSON, MaterialOwnerType.WORKING, 93L);
        when(repository.lockOwner(owner)).thenReturn(new MaterialOwner(owner, 43L));
        when(repository.requireReference(801L)).thenReturn(reference(801L, owner, 73L, false));

        service.detach(owner, 801L);

        verify(repository).detach(801L, Instant.parse("2026-09-01T12:00:00Z"));
        verify(ossService).reconcileReferences("profile_material_ref", "801", Set.of(73L), Set.of());

        when(repository.requireReference(802L)).thenReturn(reference(802L, owner, 74L, true));
        assertThatThrownBy(() -> service.detach(owner, 802L))
            .hasMessageContaining("IMMUTABLE_MATERIAL");
    }

    @Test
    void rejectsInvalidTreeShapesAndProtectsSystemRequiredTags() {
        MaterialNode root = new MaterialNode(1L, 0L, MaterialNodeType.CATEGORY, 1,
            MaterialScope.PERSON, null, "个人材料", false, true, 0, 0);
        when(repository.requireNode(1L)).thenReturn(root);

        assertThatThrownBy(() -> service.createNode(new MaterialNodeCommand(
            0L, MaterialNodeType.CATEGORY, MaterialScope.PERSON, null, "重复根", false, 1, 0)))
            .hasMessageContaining("MATERIAL_ROOT_SCOPE_FIXED");

        assertThatThrownBy(() -> service.createNode(new MaterialNodeCommand(
            1L, MaterialNodeType.CATEGORY, MaterialScope.ENTERPRISE, null, "错误分类", false, 1, 0)))
            .hasMessageContaining("MATERIAL_TREE_PROFILE_MISMATCH");

        MaterialNode protectedTag = tag(2L, ProfileType.PERSON, "ID_FRONT", true);
        when(repository.requireNodeForUpdate(2L)).thenReturn(protectedTag);
        assertThatThrownBy(() -> service.changeStatus(2L, false, 0))
            .hasMessageContaining("SYSTEM_MATERIAL_TAG_PROTECTED");
        assertThatThrownBy(() -> service.archiveNode(2L, 0))
            .hasMessageContaining("SYSTEM_MATERIAL_TAG_PROTECTED");

        MaterialNode ordinaryTag = tag(3L, ProfileType.PERSON, "OPTIONAL_OLD", false);
        when(repository.requireNodeForUpdate(3L)).thenReturn(ordinaryTag);
        assertThatThrownBy(() -> service.updateNode(3L, new MaterialNodeCommand(
            1L, MaterialNodeType.TAG, MaterialScope.PERSON, "OPTIONAL_NEW", "新名称", false, 1, 0)))
            .hasMessageContaining("MATERIAL_TAG_CODE_IMMUTABLE");
    }

    @Test
    void validatesEveryRequiredTagAgainstTheCurrentAttachedSet() {
        MaterialOwnerKey owner = new MaterialOwnerKey(ProfileType.PERSON, MaterialOwnerType.WORKING, 94L);
        when(repository.lockOwner(owner)).thenReturn(new MaterialOwner(owner, 44L));
        when(repository.requirements(ProfileType.PERSON, "CN_RESIDENT_ID", Set.of("ALWAYS")))
            .thenReturn(List.of(new MaterialRequirement("ID_FRONT", 1), new MaterialRequirement("ID_BACK", 1)));
        when(repository.attachedCountsByTag(owner)).thenReturn(java.util.Map.of("ID_FRONT", 1L));

        assertThatThrownBy(() -> service.validateRequired(owner, "CN_RESIDENT_ID", Set.of("ALWAYS")))
            .hasMessageContaining("MISSING_REQUIRED_MATERIAL:ID_BACK");
    }

    @Test
    void rejectsWorkingMaterialWritesWhenTheApplicationIsNotEditable() {
        MaterialOwnerKey owner = new MaterialOwnerKey(ProfileType.PERSON, MaterialOwnerType.WORKING, 95L);
        when(repository.lockOwner(owner)).thenReturn(new MaterialOwner(owner, 45L));
        org.mockito.Mockito.doThrow(new ProfileMaterialException("MATERIAL_OWNER_READ_ONLY"))
            .when(repository).requireWorkingEditable(owner);

        assertThatThrownBy(() -> service.attach(new MaterialAttachCommand(owner, 75L, 505L)))
            .hasMessageContaining("MATERIAL_OWNER_READ_ONLY");
        assertThatThrownBy(() -> service.detach(owner, 805L))
            .hasMessageContaining("MATERIAL_OWNER_READ_ONLY");

        verify(repository, never()).insertReference(any());
        verify(repository, never()).detach(any(), any());
    }

    @Test
    void publishesOnlyRelatedSubmissionEvidenceToItsProfileVersion() {
        MaterialOwnerKey source = new MaterialOwnerKey(ProfileType.PERSON, MaterialOwnerType.SUBMISSION, 96L);
        MaterialOwnerKey target = new MaterialOwnerKey(ProfileType.PERSON, MaterialOwnerType.VERSION, 97L);
        MaterialOwner sourceOwner = new MaterialOwner(source, 46L);
        when(repository.lockOwner(source)).thenReturn(sourceOwner);
        when(repository.lockOwner(target)).thenReturn(new MaterialOwner(target, null));
        when(repository.insertImmutableCopies(source, target, clock.instant()))
            .thenReturn(List.of(reference(806L, target, 76L, true)));

        var result = service.snapshotImmutable(source, target);

        assertThat(result).singleElement().extracting(ProfileMaterialPort.MaterialReferenceView::owner)
            .isEqualTo(target);
        verify(repository).requireSnapshotRelationship(source, target);
        verify(accessPolicy, never()).requireWrite(sourceOwner);
        verify(ossService).reconcileReferences("profile_material_ref", "806", Set.of(), Set.of(76L));
    }

    private MaterialNode tag(long id, ProfileType type, String code, boolean systemRequired) {
        return new MaterialNode(id, 1L, MaterialNodeType.TAG, 2, MaterialScope.valueOf(type.name()), code, code,
            systemRequired, true, 0, 0);
    }

    private MaterialReference reference(long id, MaterialOwnerKey owner, long ossId, boolean immutable) {
        return new MaterialReference(id, owner, ossId, 501L, "ID_FRONT", "正面", "front.jpg",
            1024L, ".jpg", "image/jpeg", true, immutable, Instant.parse("2026-09-01T11:00:00Z"), null, 0);
    }
}
