-- ----------------------------
-- NAMEWTA Client / 角色 / 菜单 RBAC 列
-- 执行前需已执行 001_user_type.sql
-- ----------------------------

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
