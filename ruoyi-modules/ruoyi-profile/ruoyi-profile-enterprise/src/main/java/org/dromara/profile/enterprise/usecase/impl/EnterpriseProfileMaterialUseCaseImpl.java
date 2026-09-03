package org.dromara.profile.enterprise.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.profile.api.material.ProfileMaterialPort;
import org.dromara.profile.enterprise.usecase.ProfileMaterialUseCase;
import org.dromara.profile.enterprise.service.EnterpriseMaterialService;
import org.springframework.stereotype.Service;

/**
 * EnterpriseProfileMaterialUseCaseImpl 应用用例合同，定义入口可调用的业务场景。
 */
@Service
@RequiredArgsConstructor
public class EnterpriseProfileMaterialUseCaseImpl implements ProfileMaterialUseCase {

    private final EnterpriseMaterialService delegate;

    /**
     * 编排 tree 应用用例。
     */
    @Override public java.util.List<ProfileMaterialPort.MaterialNodeView> tree(ProfileMaterialPort.MaterialScope scope,
                                                                                boolean includeDisabled) {
        return delegate.tree(scope, includeDisabled);
    }
    /**
     * 编排 createNode 应用用例。
     */
    @Override public ProfileMaterialPort.MaterialNodeView createNode(ProfileMaterialPort.MaterialNodeCommand command) {
        return delegate.createNode(command);
    }
    /**
     * 编排 updateNode 应用用例。
     */
    @Override public ProfileMaterialPort.MaterialNodeView updateNode(Long materialNodeId,
                                                                       ProfileMaterialPort.MaterialNodeCommand command) {
        return delegate.updateNode(materialNodeId, command);
    }
    /**
     * 编排 changeStatus 应用用例。
     */
    @Override public void changeStatus(Long materialNodeId, boolean enabled, int expectedVersion) {
        delegate.changeStatus(materialNodeId, enabled, expectedVersion);
    }
    /**
     * 编排 archiveNode 应用用例。
     */
    @Override public void archiveNode(Long materialNodeId, int expectedVersion) {
        delegate.archiveNode(materialNodeId, expectedVersion);
    }
    /**
     * 编排 attach 应用用例。
     */
    @Override public ProfileMaterialPort.MaterialReferenceView attach(ProfileMaterialPort.MaterialAttachCommand command) {
        return delegate.attach(command);
    }
    /**
     * 编排 detach 应用用例。
     */
    @Override public void detach(ProfileMaterialPort.MaterialOwnerKey owner, Long materialRefId) {
        delegate.detach(owner, materialRefId);
    }
    /**
     * 编排 list 应用用例。
     */
    @Override public java.util.List<ProfileMaterialPort.MaterialReferenceView> list(ProfileMaterialPort.MaterialOwnerKey owner) {
        return delegate.list(owner);
    }
    /**
     * 编排 accessUrl 应用用例。
     */
    @Override public org.dromara.system.api.OssService.OssAccessUrl accessUrl(ProfileMaterialPort.MaterialOwnerKey owner,
                                                                                Long materialRefId) {
        return delegate.accessUrl(owner, materialRefId);
    }
    /**
     * 编排 validateRequired 应用用例。
     */
    @Override public void validateRequired(ProfileMaterialPort.MaterialOwnerKey owner, String documentTypeCode,
                                           java.util.Set<String> conditions) {
        delegate.validateRequired(owner, documentTypeCode, conditions);
    }
    /**
     * 编排 snapshotImmutable 应用用例。
     */
    @Override public java.util.List<ProfileMaterialPort.MaterialReferenceView> snapshotImmutable(
        ProfileMaterialPort.MaterialOwnerKey source, ProfileMaterialPort.MaterialOwnerKey target) {
        return delegate.snapshotImmutable(source, target);
    }
}
