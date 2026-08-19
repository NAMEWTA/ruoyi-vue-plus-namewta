-- ----------------------------
-- NAMEWTA 初始登录域、Client 回填与演示数据
-- 执行前需已执行 001_user_type.sql、002_client_rbac.sql
-- 现有管理端 Client（e5cd7e4891bf95d1d19206ce24a7b32e）保持可登录
-- ----------------------------

-- ----------------------------
-- 两个登录域
-- ----------------------------
insert into sys_user_type values (1762100000000000001, 'sys_user', '系统用户', 1, '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '管理后台登录域');
insert into sys_user_type values (1762100000000000002, 'app_user', '应用用户', 2, '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '用户端登录域');

-- ----------------------------
-- 回填已有用户的系统登录域
-- ----------------------------
insert into sys_user_type_rel values (1762200000000000001, 1761100000000000001, 1762100000000000001, 'SYSTEM_INIT', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null);
insert into sys_user_type_rel values (1762200000000000002, 1761100000000000003, 1762100000000000001, 'SYSTEM_INIT', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null);
insert into sys_user_type_rel values (1762200000000000003, 1761100000000000004, 1762100000000000001, 'SYSTEM_INIT', '0', 1761000000000000103, 1761100000000000001, sysdate(), null, null);

-- ----------------------------
-- 已有角色、菜单归属管理端 Client（sys_client.id = 1762000000000000001）
-- ----------------------------
update sys_role set client_id = 1762000000000000001 where client_id is null;
update sys_menu set client_id = 1762000000000000001 where client_id is null;

-- ----------------------------
-- 用户端默认角色（不写入 sys_user_role，登录时按 Client 合并）
-- ----------------------------
insert into sys_role (role_id, client_id, role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly, status, del_flag, create_dept, create_by, create_time, remark)
values (1761300000000000010, 1762000000000000002, '数据采集用户', 'app_user', 1, '5', 1, 1, '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), '数据采集端默认角色'),
       (1761300000000000011, 1762000000000000003, '数超大赛用户', 'app_user', 1, '5', 1, 1, '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), '数超大赛端默认角色'),
       (1761300000000000012, 1762000000000000004, 'Token 中继用户', 'app_user', 1, '5', 1, 1, '0', '0', 1761000000000000103, 1761100000000000001, sysdate(), 'Token 中继端默认角色');

-- ----------------------------
-- 回填已有 Client，并补充两个演示用户端
-- pc / e5cd7e4891bf95d1d19206ce24a7b32e 继续作为管理后台
-- app / 428a8310cd442757ae699df5d894f051 作为数据采集端
-- ----------------------------
update sys_client
set user_type_id     = 1762100000000000001,
    register_enabled = 0,
    default_role_id  = null
where id = 1762000000000000001;

update sys_client
set user_type_id     = 1762100000000000002,
    register_enabled = 0,
    default_role_id  = 1761300000000000010
where id = 1762000000000000002;

insert into sys_client (id, client_id, client_key, client_secret, grant_type, device_type, access_path, ip_whitelist,
                        active_timeout, timeout, user_type_id, register_enabled, default_role_id, status, del_flag,
                        create_dept, create_by, create_time, update_by, update_time)
values (1762000000000000003, '78bff3d278ace982349f7e00f182b2c9', 'competition', 'comp123', 'password,sms,social', 'android',
        '/app/**', null, 1800, 604800, 1762100000000000002, 1, 1761300000000000011, '0', '0',
        1761000000000000103, 1761100000000000001, sysdate(), 1761100000000000001, sysdate()),
       (1762000000000000004, 'a03e40daa0e9212a1e0101ed4d463bed', 'relay', 'relay123', 'password,sms,social', 'android',
        '/app/**', null, 1800, 604800, 1762100000000000002, 0, 1761300000000000012, '0', '0',
        1761000000000000103, 1761100000000000001, sysdate(), 1761100000000000001, sysdate());

-- ----------------------------
-- 登录域管理菜单（归属管理端 Client）
-- ----------------------------
insert into sys_menu (menu_id, client_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, remark)
values (1761400000000000124, 1762000000000000001, '登录域管理', 1761400000000000001, 12, 'userType', 'system/userType/index', '', 'N', 'Y', 'C', '0', '0', 'system:userType:list', 'people', '', '', 1761000000000000103, 1761100000000000001, sysdate(), '登录域管理菜单');

insert into sys_menu (menu_id, client_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, remark)
values (1761400000000001070, 1762000000000000001, '登录域查询', 1761400000000000124, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'system:userType:query', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), ''),
       (1761400000000001071, 1762000000000000001, '登录域新增', 1761400000000000124, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'system:userType:add', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), ''),
       (1761400000000001072, 1762000000000000001, '登录域修改', 1761400000000000124, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'system:userType:edit', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), ''),
       (1761400000000001073, 1762000000000000001, '登录域删除', 1761400000000000124, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'system:userType:remove', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), ''),
       (1761400000000001074, 1762000000000000001, '登录域导出', 1761400000000000124, 5, '', '', '', 'N', 'Y', 'F', '0', '0', 'system:userType:export', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), '');

-- ----------------------------
-- 删除全局注册开关，改由 Client.register_enabled 控制
-- ----------------------------
delete from sys_config where config_key = 'sys.account.registerUser';
