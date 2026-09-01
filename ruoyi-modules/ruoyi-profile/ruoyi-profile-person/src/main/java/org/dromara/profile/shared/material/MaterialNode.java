package org.dromara.profile.shared.material;

import org.dromara.profile.api.material.ProfileMaterialPort.MaterialNodeType;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialScope;

public record MaterialNode(Long materialNodeId, Long parentId, MaterialNodeType nodeType, int nodeDepth,
                           MaterialScope scope, String materialTagCode, String nodeName,
                           boolean systemRequired, boolean enabled, int orderNum, int version) {
}
