package org.dromara.profile.person.domain.vo;

import org.dromara.profile.api.material.ProfileMaterialPort.MaterialReferenceView;

import java.util.List;

/** PersonProfileDetailVo 对外返回模型。 */
public record PersonProfileDetailVo(
    PersonProfileSummaryVo profile,
    List<PersonProfileVersionVo> versions,
    List<PersonProfileBindingVo> bindings,
    List<PersonProfileSourceVo> sources,
    List<PersonProfileAuditVo> audits,
    List<MaterialReferenceView> currentMaterials
) {
}
