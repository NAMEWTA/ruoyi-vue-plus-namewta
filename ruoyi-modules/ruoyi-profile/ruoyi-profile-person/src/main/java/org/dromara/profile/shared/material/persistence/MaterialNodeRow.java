package org.dromara.profile.shared.material.persistence;

public record MaterialNodeRow(Long materialNodeId, Long parentId, String nodeType, Integer nodeDepth,
                              String profileType, String materialTagCode, String nodeName,
                              String systemRequired, String status, Integer orderNum, Integer version) {
}
