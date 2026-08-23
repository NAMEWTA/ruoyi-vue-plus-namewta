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

-- ============================================================================
-- 变更标识：NAMEWTA-OSS-NOTIFY-DDL-001
-- 变更内容：OSS TEMP 生命周期、业务引用及通知监控表
-- 执行前置：已执行 NAMEWTA-BASE-DDL-002
-- 适用范围：全新环境；已完成 NAMEWTA-BASE-DDL-002 的升级环境
-- 重复执行：否
-- 回滚方式：应用回滚时保留 additive schema；确需回滚前先备份并确认无新业务数据
-- ============================================================================

-- ----------------------------
-- OSS 生命周期扩展
-- 先输出待回填行数；历史对象一律保守回填为非临时对象，避免迁移后被自动清理。
-- ----------------------------
alter table sys_oss
    add column is_temp     char(1)  default null comment '是否临时对象（Y是 N否）' after service,
    add column expire_time datetime default null comment '临时对象过期时间' after is_temp;

select count(*) as sys_oss_history_backfill_count
from sys_oss
where is_temp is null;

update sys_oss
set is_temp = 'N',
    expire_time = null
where is_temp is null;

alter table sys_oss
    modify column is_temp char(1) not null default 'N' comment '是否临时对象（Y是 N否）';

alter table sys_oss
    add key idx_sys_oss_temp_expire (is_temp, expire_time);

-- ----------------------------
-- OSS 业务引用表
-- ref_type 保存实际物理表名，ref_id 保存该表真实主键的字符串表示；不承担 ACL。
-- ----------------------------
create table sys_oss_ref (
    oss_ref_id    bigint(20)   not null                   comment 'OSS引用主键',
    oss_id        bigint(20)   not null                   comment 'OSS对象存储主键',
    ref_type      varchar(64)  not null                   comment '引用方实际物理表名',
    ref_id        varchar(64)  not null                   comment '引用方真实主键',
    version       int(11)      default 0                  comment '乐观锁版本号',
    create_dept   bigint(20)   default null               comment '创建部门',
    create_time   datetime     default null               comment '创建时间',
    create_by     bigint(20)   default null               comment '创建者',
    update_time   datetime     default null               comment '更新时间',
    update_by     bigint(20)   default null               comment '更新者',
    del_flag      char(1)      default '0'                comment '删除标志（0代表存在 1代表删除）',
    primary key (oss_ref_id),
    unique key uk_sys_oss_ref_object (oss_id, ref_type, ref_id),
    key idx_sys_oss_ref_reverse (ref_type, ref_id)
) engine=innodb comment='OSS业务引用表';

-- ----------------------------
-- 通知逻辑日志表
-- client_pk 仅记录请求来源的 sys_client.id，不构成数据隔离或路由条件。
-- ----------------------------
create table sys_notify_log (
    notify_log_id       bigint(20)    not null                   comment '通知日志主键',
    request_id          varchar(64)   not null                   comment '逻辑通知请求ID',
    original_request_id varchar(64)   default null               comment '重复请求关联的原请求ID',
    biz_type            varchar(64)   default null               comment '业务类型',
    biz_id              varchar(64)   default null               comment '业务主键',
    channel             varchar(32)   not null                   comment '通知渠道',
    provider_key        varchar(64)   default null               comment '实际供应商标识',
    subject             varchar(500)  default null               comment '最终通知主题',
    content             longtext      default null               comment '最终通知正文（明文）',
    content_type        varchar(32)   default null               comment '正文类型',
    template_code       varchar(128)  default null               comment '供应商模板编码',
    template_params     longtext      default null               comment '模板参数JSON（明文）',
    content_snapshot    longtext      default null               comment '应用认知的完整正文快照（明文）',
    attachment_oss_ids  text          default null               comment '通知附件快照OSS主键JSON',
    status              varchar(32)   not null                   comment '逻辑通知状态',
    error_code          varchar(128)  default null               comment '逻辑错误码',
    error_message       varchar(2000) default null               comment '清洗后的逻辑错误信息',
    client_pk           bigint(20)    default null               comment '请求来源客户端主键（仅审计）',
    user_id             bigint(20)    default null               comment '请求来源用户主键',
    trace_id            varchar(64)   default null               comment '链路追踪ID',
    version             int(11)       default 0                  comment '乐观锁版本号',
    create_dept         bigint(20)    default null               comment '创建部门',
    create_time         datetime      default null               comment '创建时间',
    create_by           bigint(20)    default null               comment '创建者',
    update_time         datetime      default null               comment '更新时间',
    update_by           bigint(20)    default null               comment '更新者',
    del_flag            char(1)       default '0'                comment '删除标志（0代表存在 1代表删除）',
    primary key (notify_log_id),
    unique key uk_sys_notify_log_request (request_id),
    key idx_sys_notify_log_original_request (original_request_id),
    key idx_sys_notify_log_biz (biz_type, biz_id),
    key idx_sys_notify_log_channel_status_time (channel, status, create_time),
    key idx_sys_notify_log_trace (trace_id)
) engine=innodb comment='通知逻辑日志表';

-- ----------------------------
-- 通知目标投递日志表
-- 每行对应一个物理目标的实际 Provider attempt，ACCEPTED 不等同于 DELIVERED。
-- ----------------------------
create table sys_notify_delivery_log (
    notify_delivery_log_id bigint(20)    not null                   comment '通知投递日志主键',
    notify_log_id          bigint(20)    not null                   comment '通知逻辑日志主键',
    target_type            varchar(32)   not null                   comment '目标类型',
    target_role            varchar(16)   default null               comment '目标角色（TO/CC/BCC）',
    target_value           varchar(1000) not null                   comment '完整物理目标（明文）',
    provider_key           varchar(64)   default null               comment '实际供应商标识',
    provider_message_id    varchar(255)  default null               comment '供应商消息ID',
    attempt_no             int(11)       not null default 1         comment '发送尝试次数',
    status                 varchar(32)   not null                   comment '目标投递状态',
    cost_time              bigint(20)    default 0                  comment '供应商调用耗时（毫秒）',
    error_code             varchar(128)  default null               comment '供应商错误码',
    error_message          varchar(2000) default null               comment '清洗后的供应商错误信息',
    version                int(11)       default 0                  comment '乐观锁版本号',
    create_dept            bigint(20)    default null               comment '创建部门',
    create_time            datetime      default null               comment '创建时间',
    create_by              bigint(20)    default null               comment '创建者',
    update_time            datetime      default null               comment '更新时间',
    update_by              bigint(20)    default null               comment '更新者',
    del_flag               char(1)       default '0'                comment '删除标志（0代表存在 1代表删除）',
    primary key (notify_delivery_log_id),
    key idx_sys_notify_delivery_notify (notify_log_id),
    key idx_sys_notify_delivery_status_time (status, create_time),
    key idx_sys_notify_delivery_provider_msg (provider_message_id)
) engine=innodb comment='通知目标投递日志表';

-- ============================================================================
-- 变更标识：NAMEWTA-OSS-NOTIFY-DDL-002
-- 变更内容：OSS 可重试删除状态；通知请求 ID 改为非唯一审计索引
-- 执行前置：已执行 NAMEWTA-OSS-NOTIFY-DDL-001
-- 适用范围：已完成 NAMEWTA-OSS-NOTIFY-DDL-001 的环境
-- 重复执行：否
-- 回滚方式：确认无 PENDING 对象后删除 delete_state，并恢复 request_id 唯一索引
-- ============================================================================

alter table sys_oss
    add column delete_state varchar(16) not null default 'ACTIVE'
        comment '删除状态（ACTIVE正常 PENDING等待供应商删除）' after expire_time;

alter table sys_notify_log
    drop index uk_sys_notify_log_request,
    add key idx_sys_notify_log_request (request_id);

alter table sys_notify_delivery_log
    modify column target_value varchar(1000) not null
        comment '物理目标（敏感审计策略下脱敏）';
