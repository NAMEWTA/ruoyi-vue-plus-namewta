package org.dromara.profile.person.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("profile_material_requirement")
public class ProfileMaterialRequirement extends BaseEntity {

    @TableId("material_requirement_id")
    private Long materialRequirementId;
    private String profileType;
    private String documentTypeCode;
    private String handlerCondition;
    private String materialTagCode;
    private Integer minimumCount;
    private String status;
    @Version
    private Integer version;
    @TableLogic
    private String delFlag;
}
