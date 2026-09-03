package org.dromara.profile.person.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.profile.api.material.ProfileMaterialPort;
import org.dromara.profile.person.service.ProfileMaterialService;
import org.dromara.profile.person.usecase.ProfileMaterialUseCase;
import org.springframework.stereotype.Service;

/**
 * ProfileMaterialUseCaseImpl 应用用例合同，定义入口可调用的业务场景。
 */
@Service
@RequiredArgsConstructor
public class ProfileMaterialUseCaseImpl implements ProfileMaterialUseCase {

    private final ProfileMaterialService service;

    /**
     * 编排 tree 应用用例。
     */
    @Override public java.util.List<ProfileMaterialPort.MaterialNodeView> tree(ProfileMaterialPort.MaterialScope scope,
                                                                                boolean includeDisabled) {
        return service.tree(scope, includeDisabled);
    }
    /**
     * 编排 createNode 应用用例。
     */
    @Override public ProfileMaterialPort.MaterialNodeView createNode(ProfileMaterialPort.MaterialNodeCommand command) {
        return service.createNode(command);
    }
    /**
     * 编排 updateNode 应用用例。
     */
    @Override public ProfileMaterialPort.MaterialNodeView updateNode(Long materialNodeId,
                                                                       ProfileMaterialPort.MaterialNodeCommand command) {
        return service.updateNode(materialNodeId, command);
    }
    /**
     * 编排 changeStatus 应用用例。
     */
    @Override public void changeStatus(Long materialNodeId, boolean enabled, int expectedVersion) {
        service.changeStatus(materialNodeId, enabled, expectedVersion);
    }
    /**
     * 编排 archiveNode 应用用例。
     */
    @Override public void archiveNode(Long materialNodeId, int expectedVersion) {
        service.archiveNode(materialNodeId, expectedVersion);
    }
    /**
     * 编排 attach 应用用例。
     */
    @Override public ProfileMaterialPort.MaterialReferenceView attach(ProfileMaterialPort.MaterialAttachCommand command) {
        return service.attach(command);
    }
    /**
     * 编排 detach 应用用例。
     */
    @Override public void detach(ProfileMaterialPort.MaterialOwnerKey owner, Long materialRefId) {
        service.detach(owner, materialRefId);
    }
    /**
     * 编排 list 应用用例。
     */
    @Override public java.util.List<ProfileMaterialPort.MaterialReferenceView> list(ProfileMaterialPort.MaterialOwnerKey owner) {
        return service.list(owner);
    }
    /**
     * 编排 accessUrl 应用用例。
     */
    @Override public org.dromara.system.api.OssService.OssAccessUrl accessUrl(ProfileMaterialPort.MaterialOwnerKey owner,
                                                                                Long materialRefId) {
        return service.accessUrl(owner, materialRefId);
    }
    /**
     * 编排 validateRequired 应用用例。
     */
    @Override public void validateRequired(ProfileMaterialPort.MaterialOwnerKey owner, String documentTypeCode,
                                           java.util.Set<String> conditions) {
        service.validateRequired(owner, documentTypeCode, conditions);
    }
    /**
     * 编排 snapshotImmutable 应用用例。
     */
    @Override public java.util.List<ProfileMaterialPort.MaterialReferenceView> snapshotImmutable(
        ProfileMaterialPort.MaterialOwnerKey source, ProfileMaterialPort.MaterialOwnerKey target) {
        return service.snapshotImmutable(source, target);
    }
}
