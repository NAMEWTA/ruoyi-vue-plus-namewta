package org.dromara.profile.enterprise.domain.vo;

import org.dromara.profile.api.material.ProfileMaterialPort.MaterialReferenceView;

import java.util.List;

public record EnterpriseProfileDetailVo(
    EnterpriseProfileSummaryVo profile,
    List<EnterpriseProfileVersionVo> versions,
    List<EnterpriseProfileBindingVo> bindings,
    List<EnterpriseProfileSourceVo> sources,
    List<EnterpriseProfileAuditVo> audits,
    List<MaterialReferenceView> currentMaterials
) {
}
