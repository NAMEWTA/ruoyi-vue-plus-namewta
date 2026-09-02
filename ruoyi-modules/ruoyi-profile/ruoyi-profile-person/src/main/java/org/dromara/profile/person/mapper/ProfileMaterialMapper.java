package org.dromara.profile.person.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.profile.person.domain.ProfileMaterialNode;
import org.apache.ibatis.annotations.Param;
import org.dromara.profile.person.domain.vo.MaterialNodeRow;
import org.dromara.profile.person.domain.vo.MaterialReferenceRow;
import org.dromara.profile.person.domain.vo.MaterialRequirementRow;
import org.dromara.profile.person.domain.vo.MaterialTagCountRow;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface ProfileMaterialMapper extends BaseMapperPlus<ProfileMaterialNode, ProfileMaterialNode> {

    List<MaterialNodeRow> selectNodes(@Param("scope") String scope,
                                      @Param("includeDisabled") boolean includeDisabled);

    MaterialNodeRow selectNode(@Param("materialNodeId") long materialNodeId);

    MaterialNodeRow lockNode(@Param("materialNodeId") long materialNodeId);

    int insertNode(@Param("materialNodeId") long materialNodeId, @Param("parentId") long parentId,
                   @Param("nodeType") String nodeType, @Param("nodeDepth") int nodeDepth,
                   @Param("profileType") String profileType, @Param("materialTagCode") String materialTagCode,
                   @Param("nodeName") String nodeName, @Param("systemRequired") String systemRequired,
                   @Param("orderNum") int orderNum);

    int updateNode(@Param("materialNodeId") long materialNodeId, @Param("parentId") long parentId,
                   @Param("nodeDepth") int nodeDepth, @Param("profileType") String profileType,
                   @Param("materialTagCode") String materialTagCode, @Param("nodeName") String nodeName,
                   @Param("systemRequired") String systemRequired, @Param("orderNum") int orderNum,
                   @Param("expectedVersion") int expectedVersion);

    long countChildren(@Param("id") long materialNodeId);

    long countReferences(@Param("id") long materialNodeId);

    int updateStatus(@Param("id") long materialNodeId, @Param("status") String status,
                     @Param("expectedVersion") int expectedVersion);

    int archiveNode(@Param("id") long materialNodeId, @Param("expectedVersion") int expectedVersion);

    long countAttached(@Param("profileType") String profileType, @Param("ownerType") String ownerType,
                       @Param("ownerId") long ownerId);

    int insertReference(MaterialReferenceRow row);

    MaterialReferenceRow lockReference(@Param("id") long materialRefId);

    int detachReference(@Param("id") long materialRefId, @Param("detachedTime") Instant detachedTime);

    List<MaterialReferenceRow> selectReferences(@Param("profileType") String profileType,
                                                @Param("ownerType") String ownerType,
                                                @Param("ownerId") long ownerId);

    List<MaterialRequirementRow> selectRequirements(@Param("profileType") String profileType,
                                                    @Param("documentTypeCode") String documentTypeCode,
                                                    @Param("conditions") Set<String> conditions);

    List<MaterialTagCountRow> selectAttachedCounts(@Param("profileType") String profileType,
                                                   @Param("ownerType") String ownerType,
                                                   @Param("ownerId") long ownerId);
}
