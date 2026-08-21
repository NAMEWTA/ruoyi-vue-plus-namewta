-- ============================================================================
-- NAMEWTA 表结构 SQL
-- 本文件自 2026-08-21 起只允许在文件末尾追加新的 DDL 变更块。
-- 已有 SQL 禁止修改、删除、替换或重排。
-- ============================================================================

-- ============================================================================
-- 变更标识：NAMEWTA-BASE-DDL-001
-- 变更内容：登录域定义、用户登录域关系及用户单值类型列迁移
-- 执行前置：已执行 script/sql/ry_vue.sql
-- 适用范围：全新环境；仅有 ry_vue.sql 基线的升级环境
-- 重复执行：否
-- 回滚方式：先恢复并回填 sys_user.user_type，再删除 sys_user_type_rel、sys_user_type
-- ============================================================================

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

-- ============================================================================
-- 变更标识：NAMEWTA-BASE-DDL-002
-- 变更内容：Client 登录域、注册、默认角色及角色菜单 Client 隔离字段
-- 执行前置：已执行 NAMEWTA-BASE-DDL-001
-- 适用范围：全新环境；已完成 NAMEWTA-BASE-DDL-001 的升级环境
-- 重复执行：否
-- 回滚方式：先停止相关业务，再按依赖逆序删除新增索引与字段
-- ============================================================================

alter table sys_client
    add column user_type_id      bigint(20)    default null comment '登录域ID' after timeout,
    add column register_enabled  tinyint(1)    default 0    comment '是否开放公开注册（0否 1是）' after user_type_id,
    add column default_role_id   bigint(20)    default null comment '默认角色ID' after register_enabled;

alter table sys_role
    add column client_id bigint(20) default null comment '归属客户端主键' after role_id;

alter table sys_menu
    add column client_id bigint(20) default null comment '归属客户端主键' after menu_id;

alter table sys_role add key idx_sys_role_client_id (client_id);
alter table sys_menu add key idx_sys_menu_client_id (client_id);
