-- ============================================================================
-- NAMEWTA 数据 SQL
-- 本文件中的 DSL 是项目约定的数据类 SQL，包含初始化、回填和补偿语句。
-- 本文件自 2026-08-21 起只允许在文件末尾追加新的数据变更块。
-- 已有 SQL 禁止修改、删除、替换或重排。
-- ============================================================================

-- ============================================================================
-- 变更标识：NAMEWTA-BASE-DSL-001
-- 变更内容：登录域、Client、角色、菜单及关系初始化
-- 执行前置：已完整执行 DDL.sql
-- 适用范围：全新环境；仅有 ry_vue.sql 基线且尚未执行旧 003 的升级环境
-- 重复执行：否
-- 回滚方式：按本块固定主键逆序删除新增关系与数据，并恢复被回填字段及全局注册配置
-- ============================================================================

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

-- 将登录域菜单授予管理端超管角色（sys_client.id = 1762000000000000001，OAuth client_id = e5cd7e4891bf95d1d19206ce24a7b32e）
insert into sys_role_menu (role_id, menu_id)
values (1761300000000000001, 1761400000000000124),
       (1761300000000000001, 1761400000000001070),
       (1761300000000000001, 1761400000000001071),
       (1761300000000000001, 1761400000000001072),
       (1761300000000000001, 1761400000000001073),
       (1761300000000000001, 1761400000000001074);

-- ----------------------------
-- 三个用户端基础菜单（默认角色只给本 Client 工作台，不写入 sys_user_role）
-- ----------------------------
insert into sys_menu (menu_id, client_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, remark)
values (1761400000000002001, 1762000000000000002, '数据采集工作台', 0, 1, 'collection', 'business/data-collection/index', '', 'N', 'Y', 'C', '0', '0', 'app:collection:workspace', 'dashboard', '', '', 1761000000000000103, 1761100000000000001, sysdate(), '数据采集端工作台'),
       (1761400000000002002, 1762000000000000003, '数超大赛工作台', 0, 1, 'competition', 'business/data-competition/index', '', 'N', 'Y', 'C', '0', '0', 'app:competition:workspace', 'trophy', '', '', 1761000000000000103, 1761100000000000001, sysdate(), '数超大赛端工作台'),
       (1761400000000002003, 1762000000000000004, 'Token 中继工作台', 0, 1, 'relay', 'business/token-relay/index', '', 'N', 'Y', 'C', '0', '0', 'app:relay:workspace', 'link', '', '', 1761000000000000103, 1761100000000000001, sysdate(), 'Token 中继端工作台');

insert into sys_role_menu (role_id, menu_id)
values (1761300000000000010, 1761400000000002001),
       (1761300000000000011, 1761400000000002002),
       (1761300000000000012, 1761400000000002003);

-- ----------------------------
-- 删除全局注册开关，改由 Client.register_enabled 控制
-- ----------------------------
delete from sys_config where config_key = 'sys.account.registerUser';

-- ============================================================================
-- 变更标识：NAMEWTA-BASE-DSL-002
-- 变更内容：为已执行旧基线但缺少菜单的环境补充三个用户端基础菜单
-- 执行前置：已执行 NAMEWTA-BASE-DSL-001 或旧 003_initial_data.sql
-- 适用范围：缺少对应菜单或角色菜单关系的升级环境；全新环境执行时自动无操作
-- 重复执行：是
-- 回滚方式：删除三个固定 menu_id 及对应 role_id、menu_id 关系
-- ============================================================================

insert into sys_menu (menu_id, client_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, remark)
select 1761400000000002001, 1762000000000000002, '数据采集工作台', 0, 1, 'collection', 'business/data-collection/index', '', 'N', 'Y', 'C', '0', '0', 'app:collection:workspace', 'dashboard', '', '', 1761000000000000103, 1761100000000000001, sysdate(), '数据采集端工作台'
from dual
where not exists (select 1 from sys_menu where menu_id = 1761400000000002001);

insert into sys_menu (menu_id, client_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, remark)
select 1761400000000002002, 1762000000000000003, '数超大赛工作台', 0, 1, 'competition', 'business/data-competition/index', '', 'N', 'Y', 'C', '0', '0', 'app:competition:workspace', 'trophy', '', '', 1761000000000000103, 1761100000000000001, sysdate(), '数超大赛端工作台'
from dual
where not exists (select 1 from sys_menu where menu_id = 1761400000000002002);

insert into sys_menu (menu_id, client_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, remark)
select 1761400000000002003, 1762000000000000004, 'Token 中继工作台', 0, 1, 'relay', 'business/token-relay/index', '', 'N', 'Y', 'C', '0', '0', 'app:relay:workspace', 'link', '', '', 1761000000000000103, 1761100000000000001, sysdate(), 'Token 中继端工作台'
from dual
where not exists (select 1 from sys_menu where menu_id = 1761400000000002003);

insert into sys_role_menu (role_id, menu_id)
select 1761300000000000010, 1761400000000002001
from dual
where not exists (select 1 from sys_role_menu where role_id = 1761300000000000010 and menu_id = 1761400000000002001);

insert into sys_role_menu (role_id, menu_id)
select 1761300000000000011, 1761400000000002002
from dual
where not exists (select 1 from sys_role_menu where role_id = 1761300000000000011 and menu_id = 1761400000000002002);

insert into sys_role_menu (role_id, menu_id)
select 1761300000000000012, 1761400000000002003
from dual
where not exists (select 1 from sys_role_menu where role_id = 1761300000000000012 and menu_id = 1761400000000002003);

-- ============================================================================
-- 变更标识：NAMEWTA-OSS-NOTIFY-DSL-001
-- 变更内容：通知监控动态菜单与功能权限
-- 执行前置：已执行 NAMEWTA-OSS-NOTIFY-DDL-001
-- 适用范围：全新环境；已完成 NAMEWTA-BASE-DSL-002 的升级环境
-- 重复执行：是
-- 回滚方式：先撤销角色授权，再删除以下固定 menu_id；不删除通知日志数据
-- ============================================================================

-- 通知监控是全局运维功能。此处只定义菜单和权限，不自动扩大普通角色授权。
insert into sys_menu (menu_id, client_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, remark)
select 1761400000000000125, 1762000000000000001, '通知监控', 1761400000000000108, 3, 'notify', 'monitor/notify/index', '', 'N', 'Y', 'C', '0', '0', 'system:notify:list', 'message', '', '', 1761000000000000103, 1761100000000000001, sysdate(), '通知监控菜单'
from dual
where not exists (select 1 from sys_menu where menu_id = 1761400000000000125);

insert into sys_menu (menu_id, client_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, remark)
select 1761400000000001080, 1762000000000000001, '通知查询', 1761400000000000125, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'system:notify:query', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), ''
from dual
where not exists (select 1 from sys_menu where menu_id = 1761400000000001080);

insert into sys_menu (menu_id, client_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, remark)
select 1761400000000001081, 1762000000000000001, '通知删除', 1761400000000000125, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'system:notify:remove', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), ''
from dual
where not exists (select 1 from sys_menu where menu_id = 1761400000000001081);

-- ============================================================================
-- NAMEWTA-BASE-DSL-003
-- 变更内容：下线尚未具备业务合同的三个用户端工作台菜单
-- 变更标识：2026-08-24_01:42:05
-- 执行前置：已执行 NAMEWTA-BASE-DSL-002
-- 适用范围：fresh 与已存在三个用户端基础菜单的 upgrade 环境
-- 重复执行：是
-- 回滚方式：业务页面完成后将三个 menu_id 的 visible/status 恢复为 '0'，并按固定 role_id/menu_id 恢复三条关系
-- ============================================================================

delete from sys_role_menu
where (role_id = 1761300000000000010 and menu_id = 1761400000000002001)
   or (role_id = 1761300000000000011 and menu_id = 1761400000000002002)
   or (role_id = 1761300000000000012 and menu_id = 1761400000000002003);

update sys_menu
set visible = '1',
    status = '1',
    update_by = 1761100000000000001,
    update_time = sysdate()
where menu_id in (1761400000000002001, 1761400000000002002, 1761400000000002003);

-- NAMEWTA-BASE-DSL-003-END

-- NAMEWTA-PASSWORD-DSL-001
-- ============================================================================
-- 变更内容：启用统一密码策略、随机化退役初始密码并新增临时密码独立权限
-- 变更标识：2026-08-28_18:21:43
-- 执行前置：已按 ry_vue.sql -> NAMEWTA DDL.sql -> NAMEWTA DML.sql 顺序建立当前基线；发布前暂停旧密码写入口
-- 适用范围：fresh 与尚未执行本块的 upgrade 环境；只支持 MySQL 8.4
-- 重复执行：是；策略或菜单已按本块 ID 存在时保留当前配置，旧键仅首次随机化
-- 回滚方式：按下方回滚步骤使用迁移前备份恢复旧键，删除本块 config/menu；绝不修改 sys_user.password
-- ============================================================================

-- 前置不符时利用 CHECK 约束立即停止，避免在 key、ID 或父菜单冲突时部分写入。
drop temporary table if exists namewta_password_dsl_001_preflight;
create temporary table namewta_password_dsl_001_preflight (
    preflight_ok tinyint not null,
    constraint chk_namewta_password_dsl_001_preflight check (preflight_ok = 1)
);

insert into namewta_password_dsl_001_preflight (preflight_ok)
select if(
    (select count(*) from sys_config where config_key = 'sys.user.initPassword') = 1
    and (select count(*) from sys_config where config_key = 'sys.user.passwordPolicy') <= 1
    and not exists (
        select 1 from sys_config
        where config_id = 2093282875312267265 and not (config_key <=> 'sys.user.passwordPolicy')
    )
    and not exists (
        select 1 from sys_config
        where config_key = 'sys.user.passwordPolicy' and config_id <> 2093282875312267265
    )
    and exists (
        select 1 from sys_menu
        where menu_id = 1761400000000000100 and menu_type = 'C' and perms = 'system:user:list'
    )
    and (select count(*) from sys_menu where perms = 'system:user:temporaryPassword') <= 1
    and not exists (
        select 1 from sys_menu
        where menu_id = 2093282875312267266 and not (perms <=> 'system:user:temporaryPassword')
    )
    and not exists (
        select 1 from sys_menu
        where perms = 'system:user:temporaryPassword' and menu_id <> 2093282875312267266
    )
    and not exists (
        select 1 from sys_menu
        where menu_id = 2093282875312267266
          and not (client_id <=> 1762000000000000001
              and parent_id <=> 1761400000000000100
              and menu_type <=> 'F')
    ),
    1,
    0
);

drop temporary table namewta_password_dsl_001_preflight;

-- 旧 backend 在滚动窗口仍读取此键。首次执行时生成每环境独立、满足 v1 四类规则的兼容值；不输出生成结果。
update sys_config
set config_value = concat(char(65), char(97), char(49), char(33), upper(hex(random_bytes(8)))),
    update_by = 1761100000000000001,
    update_time = sysdate(),
    remark = concat_ws('；', nullif(remark, ''), 'NAMEWTA-PASSWORD-DSL-001：旧键已随机化并退役')
where config_key = 'sys.user.initPassword'
  and remark not like '%NAMEWTA-PASSWORD-DSL-001%';

insert into sys_config (config_id, config_name, config_key, config_value, config_type,
                        create_dept, create_by, create_time, update_by, update_time, remark)
select 2093282875312267265, '统一密码策略', 'sys.user.passwordPolicy',
       '{"version":1,"minimumLength":8,"maximumLength":30,"requireUppercase":true,"requireLowercase":true,"requireDigit":true,"requireSpecial":true,"allowedSpecialCharacters":"@$!%*?&","generator":{"length":12,"uppercaseCharacters":"ABCDEFGHJKLMNPQRSTUVWXYZ","lowercaseCharacters":"abcdefghijkmnopqrstuvwxyz","digitCharacters":"23456789","specialCharacters":"@$!%*?&"},"defaultPassword":{"mode":"RANDOM"}}',
       'Y', 1761000000000000103, 1761100000000000001, sysdate(), null, null,
       '统一密码策略 v1；保存后必须刷新 sys_config 集群缓存'
from dual
where not exists (select 1 from sys_config where config_id = 2093282875312267265);

-- 临时密码签发不继承永久重置权限；只定义功能权限，不自动授予普通角色。
insert into sys_menu (menu_id, client_id, menu_name, parent_id, order_num, path, component, query_param,
                      is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu, ext,
                      create_dept, create_by, create_time, remark)
select 2093282875312267266, 1762000000000000001, '签发临时密码', 1761400000000000100, 8, '', '', '',
       'N', 'Y', 'F', '0', '0', 'system:user:temporaryPassword', '#', '', '',
       1761000000000000103, 1761100000000000001, sysdate(), '60 秒、用户级、单次消费的临时密码签发权限'
from dual
where not exists (select 1 from sys_menu where menu_id = 2093282875312267266);

-- 回滚步骤（仅在 backend 回滚前执行）：
-- 1. 先回滚 frontend，并撤销各普通角色对 menu_id 2093282875312267266 的显式授权。
-- 2. 从迁移前加密备份恢复 sys.user.initPassword 的 config_value、remark 和审计字段；禁止恢复公开弱默认值。
-- 3. 删除 menu_id 2093282875312267266 及其 sys_role_menu 关系，再删除 config_id 2093282875312267265。
-- 4. 刷新 sys_config、菜单和权限的 Redis/JVM 缓存，确认旧 backend 健康后才恢复写入口。
-- 前向补偿：若 backend 已全部切换，不回退用户密码；修正冲突数据后重放本块，并刷新配置/权限缓存。

-- NAMEWTA-PASSWORD-DSL-001-END
