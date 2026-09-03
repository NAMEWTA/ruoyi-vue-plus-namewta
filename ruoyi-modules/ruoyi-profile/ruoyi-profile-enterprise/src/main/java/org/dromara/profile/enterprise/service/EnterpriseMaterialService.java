package org.dromara.profile.enterprise.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 企业侧材料能力服务适配器，转发统一的材料目录和关联操作。
 *
 * Enterprise-owned service adapter for the shared material capability.
 */
@Service
@RequiredArgsConstructor
public class EnterpriseMaterialService {

    private final org.dromara.profile.api.material.ProfileMaterialPort materials;

    /**
     * 查询材料目录树
     */
    public java.util.List<org.dromara.profile.api.material.ProfileMaterialPort.MaterialNodeView> tree(
        org.dromara.profile.api.material.ProfileMaterialPort.MaterialScope scope, boolean includeDisabled) {
        return materials.tree(scope, includeDisabled);
    }
    /**
     * 创建材料节点
     */
    public org.dromara.profile.api.material.ProfileMaterialPort.MaterialNodeView createNode(
        org.dromara.profile.api.material.ProfileMaterialPort.MaterialNodeCommand command) { return materials.createNode(command); }
    /**
     * 更新node。
     */
    public org.dromara.profile.api.material.ProfileMaterialPort.MaterialNodeView updateNode(Long materialNodeId,
        org.dromara.profile.api.material.ProfileMaterialPort.MaterialNodeCommand command) {
        return materials.updateNode(materialNodeId, command);
    }
    /**
     * 变更材料节点状态
     */
    public void changeStatus(Long materialNodeId, boolean enabled, int expectedVersion) {
        materials.changeStatus(materialNodeId, enabled, expectedVersion);
    }
    /**
     * 归档材料节点
     */
    public void archiveNode(Long materialNodeId, int expectedVersion) {
        materials.archiveNode(materialNodeId, expectedVersion);
    }
    /**
     * 关联材料到业务对象
     */
    public org.dromara.profile.api.material.ProfileMaterialPort.MaterialReferenceView attach(
        org.dromara.profile.api.material.ProfileMaterialPort.MaterialAttachCommand command) { return materials.attach(command); }
    /**
     * 解除业务对象与材料的关联
     */
    public void detach(org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerKey owner, Long materialRefId) {
        materials.detach(owner, materialRefId);
    }
    /**
     * 查询材料列表
     */
    public java.util.List<org.dromara.profile.api.material.ProfileMaterialPort.MaterialReferenceView> list(
        org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerKey owner) { return materials.list(owner); }
    /**
     * 生成材料访问地址
     */
    public org.dromara.system.api.OssService.OssAccessUrl accessUrl(
        org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerKey owner, Long materialRefId) {
        return materials.accessUrl(owner, materialRefId);
    }
    /**
     * 校验必需材料是否齐全
     */
    public void validateRequired(org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerKey owner,
                                 String documentTypeCode, java.util.Set<String> conditions) {
        materials.validateRequired(owner, documentTypeCode, conditions);
    }
    /**
     * 冻结材料快照数据
     */
    public java.util.List<org.dromara.profile.api.material.ProfileMaterialPort.MaterialReferenceView> snapshotImmutable(
        org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerKey source,
        org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerKey target) {
        return materials.snapshotImmutable(source, target);
    }
}
