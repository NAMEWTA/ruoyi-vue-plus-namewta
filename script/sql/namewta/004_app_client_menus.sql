-- ----------------------------
-- 已执行过 003 的库补三个用户端基础菜单
-- 全新库只需执行 001 → 002 → 003，不必再跑本脚本
-- ----------------------------

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
