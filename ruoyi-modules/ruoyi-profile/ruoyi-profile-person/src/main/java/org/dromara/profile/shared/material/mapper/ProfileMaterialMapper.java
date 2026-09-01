package org.dromara.profile.shared.material.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.profile.shared.material.persistence.MaterialNodeRow;
import org.dromara.profile.shared.material.persistence.MaterialReferenceRow;
import org.dromara.profile.shared.material.persistence.MaterialRequirementRow;
import org.dromara.profile.shared.material.persistence.MaterialTagCountRow;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface ProfileMaterialMapper {

    @Select("""
        select material_node_id, parent_id, node_type, node_depth, profile_type, material_tag_code,
               node_name, system_required, status, order_num, version
          from profile_material_node
         where del_flag = '0'
           and (profile_type = #{scope} or (#{scope} <> 'COMMON' and profile_type = 'COMMON'))
           and (#{includeDisabled} = true or status = '0')
         order by node_depth, order_num, material_node_id
        """)
    List<MaterialNodeRow> selectNodes(@Param("scope") String scope,
                                      @Param("includeDisabled") boolean includeDisabled);

    @Select("""
        select material_node_id, parent_id, node_type, node_depth, profile_type, material_tag_code,
               node_name, system_required, status, order_num, version
          from profile_material_node
         where material_node_id = #{materialNodeId} and del_flag = '0'
        """)
    MaterialNodeRow selectNode(@Param("materialNodeId") long materialNodeId);

    @Select("""
        select material_node_id, parent_id, node_type, node_depth, profile_type, material_tag_code,
               node_name, system_required, status, order_num, version
          from profile_material_node
         where material_node_id = #{materialNodeId} and del_flag = '0'
         for update
        """)
    MaterialNodeRow lockNode(@Param("materialNodeId") long materialNodeId);

    @Insert("""
        insert into profile_material_node (
            material_node_id, parent_id, node_type, node_depth, profile_type, material_tag_code,
            node_name, system_required, status, order_num, version, create_dept, create_time, create_by,
            update_time, update_by, del_flag
        ) values (
            #{materialNodeId}, #{parentId}, #{nodeType}, #{nodeDepth}, #{profileType}, #{materialTagCode},
            #{nodeName}, #{systemRequired}, '0', #{orderNum}, 0, -1, current_timestamp, -1,
            current_timestamp, -1, '0'
        )
        """)
    int insertNode(@Param("materialNodeId") long materialNodeId, @Param("parentId") long parentId,
                   @Param("nodeType") String nodeType, @Param("nodeDepth") int nodeDepth,
                   @Param("profileType") String profileType, @Param("materialTagCode") String materialTagCode,
                   @Param("nodeName") String nodeName, @Param("systemRequired") String systemRequired,
                   @Param("orderNum") int orderNum);

    @Update("""
        update profile_material_node
           set parent_id = #{parentId}, node_depth = #{nodeDepth}, profile_type = #{profileType},
               material_tag_code = #{materialTagCode}, node_name = #{nodeName},
               system_required = #{systemRequired}, order_num = #{orderNum}, version = version + 1,
               update_time = current_timestamp, update_by = -1
         where material_node_id = #{materialNodeId} and version = #{expectedVersion} and del_flag = '0'
        """)
    int updateNode(@Param("materialNodeId") long materialNodeId, @Param("parentId") long parentId,
                   @Param("nodeDepth") int nodeDepth, @Param("profileType") String profileType,
                   @Param("materialTagCode") String materialTagCode, @Param("nodeName") String nodeName,
                   @Param("systemRequired") String systemRequired, @Param("orderNum") int orderNum,
                   @Param("expectedVersion") int expectedVersion);

    @Select("select count(*) from profile_material_node where parent_id = #{id} and del_flag = '0'")
    long countChildren(@Param("id") long materialNodeId);

    @Select("select count(*) from profile_material_ref where material_node_id = #{id} and del_flag = '0'")
    long countReferences(@Param("id") long materialNodeId);

    @Update("""
        update profile_material_node
           set status = #{status}, version = version + 1, update_time = current_timestamp, update_by = -1
         where material_node_id = #{id} and version = #{expectedVersion} and del_flag = '0'
        """)
    int updateStatus(@Param("id") long materialNodeId, @Param("status") String status,
                     @Param("expectedVersion") int expectedVersion);

    @Update("""
        update profile_material_node
           set del_flag = '1', status = '1', version = version + 1,
               update_time = current_timestamp, update_by = -1
         where material_node_id = #{id} and version = #{expectedVersion} and del_flag = '0'
        """)
    int archiveNode(@Param("id") long materialNodeId, @Param("expectedVersion") int expectedVersion);

    @Select("""
        select applicant_user_id
          from profile_person_application
         where person_application_id = #{ownerId} and del_flag = '0'
         for update
        """)
    Long lockPersonApplication(@Param("ownerId") long ownerId);

    @Select("""
        select applicant_user_id
          from profile_enterprise_application
         where enterprise_application_id = #{ownerId} and del_flag = '0'
         for update
        """)
    Long lockEnterpriseApplication(@Param("ownerId") long ownerId);

    @Select("""
        select a.applicant_user_id
          from profile_person_submission s
          join profile_person_application a on a.person_application_id = s.person_application_id
         where s.person_submission_id = #{ownerId} and s.del_flag = '0' and a.del_flag = '0'
         for update
        """)
    Long lockPersonSubmissionOwner(@Param("ownerId") long ownerId);

    @Select("""
        select a.applicant_user_id
          from profile_enterprise_submission s
          join profile_enterprise_application a on a.enterprise_application_id = s.enterprise_application_id
         where s.enterprise_submission_id = #{ownerId} and s.del_flag = '0' and a.del_flag = '0'
         for update
        """)
    Long lockEnterpriseSubmissionOwner(@Param("ownerId") long ownerId);

    @Select("""
        <script>
        select count(*) from (
            select person_source_id as id from profile_person_source where #{profileType} = 'PERSON' and #{ownerType} = 'SOURCE' and person_source_id = #{ownerId} and del_flag = '0'
            union all select person_version_id from profile_person_version where #{profileType} = 'PERSON' and #{ownerType} = 'VERSION' and person_version_id = #{ownerId} and del_flag = '0'
            union all select enterprise_source_id from profile_enterprise_source where #{profileType} = 'ENTERPRISE' and #{ownerType} = 'SOURCE' and enterprise_source_id = #{ownerId} and del_flag = '0'
            union all select enterprise_version_id from profile_enterprise_version where #{profileType} = 'ENTERPRISE' and #{ownerType} = 'VERSION' and enterprise_version_id = #{ownerId} and del_flag = '0'
        ) owners
        </script>
        """)
    long countImmutableOwner(@Param("profileType") String profileType, @Param("ownerType") String ownerType,
                             @Param("ownerId") long ownerId);

    @Select("""
        select count(*) from profile_material_ref
         where profile_type = #{profileType} and owner_type = #{ownerType} and owner_id = #{ownerId}
           and status = 'ATTACHED' and del_flag = '0'
        """)
    long countAttached(@Param("profileType") String profileType, @Param("ownerType") String ownerType,
                       @Param("ownerId") long ownerId);

    @Insert("""
        insert into profile_material_ref (
            material_ref_id, owner_type, owner_id, profile_type, oss_id, material_node_id,
            material_tag_code, material_tag_name, file_name, file_size, file_extension, mime_type,
            status, immutable_flag, attached_time, detached_time, version,
            create_dept, create_time, create_by, update_time, update_by, del_flag
        ) values (
            #{materialRefId}, #{ownerType}, #{ownerId}, #{profileType}, #{ossId}, #{materialNodeId},
            #{materialTagCode}, #{materialTagName}, #{fileName}, #{fileSize}, #{fileExtension}, #{mimeType},
            #{status}, #{immutableFlag}, #{attachedTime}, #{detachedTime}, #{version},
            -1, current_timestamp, -1, current_timestamp, -1, '0'
        )
        """)
    int insertReference(MaterialReferenceRow row);

    @Select("""
        select material_ref_id, owner_type, owner_id, profile_type, oss_id, material_node_id,
               material_tag_code, material_tag_name, file_name, file_size, file_extension, mime_type,
               status, immutable_flag, attached_time, detached_time, version
          from profile_material_ref
         where material_ref_id = #{id} and del_flag = '0'
         for update
        """)
    MaterialReferenceRow lockReference(@Param("id") long materialRefId);

    @Update("""
        update profile_material_ref
           set status = 'DETACHED', detached_time = #{detachedTime}, version = version + 1,
               update_time = current_timestamp, update_by = -1
         where material_ref_id = #{id} and status = 'ATTACHED' and immutable_flag = 'N' and del_flag = '0'
        """)
    int detachReference(@Param("id") long materialRefId, @Param("detachedTime") Instant detachedTime);

    @Select("""
        select material_ref_id, owner_type, owner_id, profile_type, oss_id, material_node_id,
               material_tag_code, material_tag_name, file_name, file_size, file_extension, mime_type,
               status, immutable_flag, attached_time, detached_time, version
          from profile_material_ref
         where profile_type = #{profileType} and owner_type = #{ownerType} and owner_id = #{ownerId}
           and del_flag = '0'
         order by attached_time, material_ref_id
        """)
    List<MaterialReferenceRow> selectReferences(@Param("profileType") String profileType,
                                                @Param("ownerType") String ownerType,
                                                @Param("ownerId") long ownerId);

    @Select("""
        <script>
        select material_tag_code, minimum_count
          from profile_material_requirement
         where profile_type = #{profileType} and status = '0' and del_flag = '0'
           and (document_type_code = '*' or document_type_code = #{documentTypeCode})
           and (handler_condition = 'ALWAYS'
             <if test="conditions != null and !conditions.isEmpty()">
               or handler_condition in
               <foreach collection="conditions" item="condition" open="(" separator="," close=")">#{condition}</foreach>
             </if>)
         order by material_requirement_id
        </script>
        """)
    List<MaterialRequirementRow> selectRequirements(@Param("profileType") String profileType,
                                                    @Param("documentTypeCode") String documentTypeCode,
                                                    @Param("conditions") Set<String> conditions);

    @Select("""
        select material_tag_code, count(*) as material_count
          from profile_material_ref
         where profile_type = #{profileType} and owner_type = #{ownerType} and owner_id = #{ownerId}
           and status = 'ATTACHED' and del_flag = '0'
         group by material_tag_code
        """)
    List<MaterialTagCountRow> selectAttachedCounts(@Param("profileType") String profileType,
                                                   @Param("ownerType") String ownerType,
                                                   @Param("ownerId") long ownerId);
}
