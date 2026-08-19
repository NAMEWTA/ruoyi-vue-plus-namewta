-- ----------------------------
-- NAMEWTA 登录域定义与用户关系
-- 执行前需已导入 script/sql/ry_vue.sql
-- ----------------------------

-- ----------------------------
-- 登录域定义表
-- ----------------------------
create table sys_user_type (
    user_type_id    bigint(20)      not null                   comment '登录域ID',
    user_type_code  varchar(32)     not null                   comment '登录域编码',
    user_type_name  varchar(30)     not null                   comment '登录域名称',
    order_num       int(4)          default 0                  comment '显示顺序',
    status          char(1)         default '0'                comment '状态（0正常 1停用）',
    del_flag        char(1)         default '0'                comment '删除标志（0代表存在 1代表删除）',
    create_dept     bigint(20)      default null               comment '创建部门',
    create_by       bigint(20)      default null               comment '创建者',
    create_time     datetime                                   comment '创建时间',
    update_by       bigint(20)      default null               comment '更新者',
    update_time     datetime                                   comment '更新时间',
    remark          varchar(500)    default null               comment '备注',
    primary key (user_type_id),
    unique key uk_sys_user_type_code (user_type_code)
) engine=innodb comment = '登录域定义表';

-- ----------------------------
-- 用户登录域关系表
-- ----------------------------
create table sys_user_type_rel (
    rel_id          bigint(20)      not null                   comment '关系ID',
    user_id         bigint(20)      not null                   comment '用户ID',
    user_type_id    bigint(20)      not null                   comment '登录域ID',
    grant_source    varchar(32)     not null                   comment '授权来源（SELF_REGISTER/ADMIN_CREATE/ADMIN_GRANT/SYSTEM_INIT）',
    status          char(1)         default '0'                comment '状态（0正常 1停用）',
    create_dept     bigint(20)      default null               comment '创建部门',
    create_by       bigint(20)      default null               comment '创建者',
    create_time     datetime                                   comment '创建时间',
    update_by       bigint(20)      default null               comment '更新者',
    update_time     datetime                                   comment '更新时间',
    primary key (rel_id),
    unique key uk_sys_user_type_rel (user_id, user_type_id),
    key idx_sys_user_type_rel_type (user_type_id)
) engine=innodb comment = '用户登录域关系表';

-- ----------------------------
-- 删除用户单值类型列（登录域改为关系表）
-- ----------------------------
alter table sys_user drop column user_type;
