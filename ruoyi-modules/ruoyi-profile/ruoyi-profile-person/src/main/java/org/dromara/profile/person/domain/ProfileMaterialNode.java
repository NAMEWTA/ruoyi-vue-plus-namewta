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
@TableName("profile_material_node")
public class ProfileMaterialNode extends BaseEntity {

    @TableId("material_node_id")
    private Long materialNodeId;
    private Long parentId;
    private String nodeType;
    private Integer nodeDepth;
    private String profileType;
    private String materialTagCode;
    private String nodeName;
    private String systemRequired;
    private String status;
    private Integer orderNum;
    @Version
    private Integer version;
    @TableLogic
    private String delFlag;
}
