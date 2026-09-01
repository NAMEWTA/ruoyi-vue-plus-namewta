SET NAMES utf8mb4;

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

-- ============================================================================
-- 变更标识：NAMEWTA-RUNTIME-GEN-RETIRE-DDL-001
-- 变更内容：永久删除运行时代码生成器元数据表
-- 执行前置：冻结基线已创建 gen_table_column 与 gen_table
-- 适用范围：全新或当前 NAMEWTA 基座初始化
-- 重复执行：否
-- 恢复方式：无；不备份、不归档、不重建兼容表
-- ============================================================================

drop table gen_table_column;
drop table gen_table;

-- NAMEWTA-OPENAPI-CREDENTIAL-DDL-001
-- ============================================================================
-- 变更内容：新增每用户唯一的 OpenAPI 凭据表
-- 变更标识：2026-08-31_22:02:33
-- 执行前置：已完整执行 NAMEWTA 基线 DDL；应用仍保持 openapi.enabled=false
-- 适用范围：全新或当前 NAMEWTA 基座初始化
-- 重复执行：否
-- 回滚方式：停用 OpenAPI 并确认无需保留凭据后 drop table sys_open_api_credential
-- ============================================================================

create table sys_open_api_credential (
    open_api_credential_id bigint(20)    not null                   comment 'OpenAPI凭据主键',
    owner_user_id          bigint(20)    not null                   comment '凭据所属用户主键',
    active_owner_user_id   bigint(20)    generated always as
        (case when del_flag = '0' then owner_user_id else null end) stored comment '未删除凭据所属用户唯一键',
    app_key                varchar(64)   not null                   comment '公开应用标识',
    app_name               varchar(100)  not null                   comment '应用名称',
    secret_ciphertext      varbinary(512) not null                  comment 'AES-256-GCM密文',
    secret_nonce           binary(12)    not null                   comment 'AES-GCM随机nonce',
    secret_tag             binary(16)    not null                   comment 'AES-GCM认证标签',
    kek_version            varchar(64)   not null                   comment '密钥加密密钥版本',
    status                 char(1)       not null default '0'       comment '状态（0启用 1停用）',
    expires_at             datetime      default null               comment '过期时间（空为永久）',
    remark                 varchar(500)  default null               comment '备注',
    version                int(11)       not null default 0         comment '乐观锁版本号',
    create_dept            bigint(20)    default null               comment '创建部门',
    create_time            datetime      default null               comment '创建时间',
    create_by              bigint(20)    default null               comment '创建者',
    update_time            datetime      default null               comment '更新时间',
    update_by              bigint(20)    default null               comment '更新者',
    del_flag               char(1)       not null default '0'       comment '删除标志（0代表存在 1代表删除）',
    primary key (open_api_credential_id),
    unique key uk_sys_open_api_credential_active_owner (active_owner_user_id),
    unique key uk_sys_open_api_credential_app_key (app_key),
    key idx_sys_open_api_credential_owner (owner_user_id)
) engine=innodb comment='用户OpenAPI凭据表';

-- 变更内容：收敛OSS访问类型并新增可审计的存储边界迁移表
-- 变更标识：2026-09-01_00:14:13
-- 执行前置：已执行 NAMEWTA-OPENAPI-CREDENTIAL-DDL-001；随后必须执行同标识的DML安全回填
-- 适用范围：全新环境；尚未应用本变更的升级环境
-- 重复执行：否
-- 回滚方式：应用回滚时保留additive迁移表；访问类型语义只允许前向修复，不恢复旧public/custom解释
-- 逻辑标识：NAMEWTA-OSS-ACCESS-DDL-001
-- ============================================================================

alter table sys_oss_config
    modify column access_policy char(1) not null default '0'
        comment '桶权限类型（0=PRIVATE 2=PUBLIC_READ）';

create table sys_oss_migration_batch (
    oss_migration_batch_id bigint(20)    not null                   comment 'OSS迁移批次主键',
    target_config_key      varchar(20)   not null                   comment '目标OSS配置标识',
    status                 varchar(32)   not null                   comment '批次状态',
    dry_run                char(1)       not null default 'Y'       comment '是否仅预检（Y是 N否）',
    total_count            int(11)       not null default 0         comment '对象总数',
    success_count          int(11)       not null default 0         comment '成功对象数',
    failed_count           int(11)       not null default 0         comment '失败对象数',
    started_time           datetime      default null               comment '开始时间',
    completed_time         datetime      default null               comment '完成时间',
    error_message          varchar(2000) default null               comment '清洗后的批次错误信息',
    version                int(11)       not null default 0         comment '乐观锁版本号',
    create_dept            bigint(20)    default null               comment '创建部门',
    create_time            datetime      default null               comment '创建时间',
    create_by              bigint(20)    default null               comment '创建者',
    update_time            datetime      default null               comment '更新时间',
    update_by              bigint(20)    default null               comment '更新者',
    del_flag               char(1)       not null default '0'       comment '删除标志（0代表存在 1代表删除）',
    primary key (oss_migration_batch_id),
    key idx_sys_oss_migration_batch_status_time (status, create_time),
    key idx_sys_oss_migration_batch_target (target_config_key)
) engine=innodb comment='OSS存储边界迁移批次表';

create table sys_oss_migration_item (
    oss_migration_item_id bigint(20)    not null                   comment 'OSS迁移明细主键',
    oss_migration_batch_id bigint(20)   not null                   comment 'OSS迁移批次主键',
    oss_id                 bigint(20)   not null                   comment 'OSS对象存储主键',
    source_config_key      varchar(20)  not null                   comment '来源OSS配置标识',
    target_config_key      varchar(20)  not null                   comment '目标OSS配置标识',
    object_key             varchar(1024) not null                  comment '供应商对象键',
    status                 varchar(32)  not null                   comment '明细状态',
    stage                  varchar(32)  not null                   comment '当前迁移阶段',
    source_size            bigint(20)   default null               comment '来源对象字节数',
    target_size            bigint(20)   default null               comment '目标对象字节数',
    source_etag            varchar(255) default null               comment '来源对象内容标识',
    target_etag            varchar(255) default null               comment '目标对象内容标识',
    retry_count            int(11)      not null default 0         comment '重试次数',
    last_error_stage       varchar(32)  default null               comment '最近失败阶段',
    error_message          varchar(2000) default null              comment '清洗后的明细错误信息',
    service_switched_time  datetime     default null               comment '服务归属切换时间',
    cleanup_eligible_time  datetime     default null               comment '允许源对象清理时间',
    cleaned_time           datetime     default null               comment '源对象清理完成时间',
    version                int(11)      not null default 0         comment '乐观锁版本号',
    create_dept            bigint(20)   default null               comment '创建部门',
    create_time            datetime     default null               comment '创建时间',
    create_by              bigint(20)   default null               comment '创建者',
    update_time            datetime     default null               comment '更新时间',
    update_by              bigint(20)   default null               comment '更新者',
    del_flag               char(1)      not null default '0'       comment '删除标志（0代表存在 1代表删除）',
    primary key (oss_migration_item_id),
    unique key uk_sys_oss_migration_item_batch_oss (oss_migration_batch_id, oss_id),
    key idx_sys_oss_migration_item_batch_status (oss_migration_batch_id, status),
    key idx_sys_oss_migration_item_oss (oss_id),
    key idx_sys_oss_migration_item_source (source_config_key),
    key idx_sys_oss_migration_item_target (target_config_key)
) engine=innodb comment='OSS存储边界迁移明细表';

-- NAMEWTA-ADMIN-RUNTIME-RECONCILE-DDL-001
-- ============================================================================
-- 变更内容：在升级环境中幂等删除已退役代码生成器元数据表
-- 执行前置：目标表不存在，或仍匹配冻结生成器主键列且两表均为空；用户已明确接受无备份风险
-- 适用范围：全新初始化或尚未执行生成器表退役的 NAMEWTA 升级环境
-- 重复执行：是
-- 恢复方式：本次无迁移前备份；失败时停止后续发布并前向修复，应用代码不再提供生成器兼容能力
-- ============================================================================

set @namewta_gen_table_rows = 0;
set @namewta_gen_column_rows = 0;

set @namewta_gen_table_count_sql = if(
    exists (
        select 1 from information_schema.tables
        where table_schema = database() and table_name = 'gen_table'
    ),
    'select count(*) into @namewta_gen_table_rows from gen_table',
    'select 0 into @namewta_gen_table_rows'
);
prepare namewta_gen_table_count_stmt from @namewta_gen_table_count_sql;
execute namewta_gen_table_count_stmt;
deallocate prepare namewta_gen_table_count_stmt;

set @namewta_gen_column_count_sql = if(
    exists (
        select 1 from information_schema.tables
        where table_schema = database() and table_name = 'gen_table_column'
    ),
    'select count(*) into @namewta_gen_column_rows from gen_table_column',
    'select 0 into @namewta_gen_column_rows'
);
prepare namewta_gen_column_count_stmt from @namewta_gen_column_count_sql;
execute namewta_gen_column_count_stmt;
deallocate prepare namewta_gen_column_count_stmt;

drop temporary table if exists namewta_admin_runtime_reconcile_ddl_001_preflight;
create temporary table namewta_admin_runtime_reconcile_ddl_001_preflight (
    preflight_ok tinyint not null,
    constraint chk_namewta_admin_runtime_reconcile_ddl_001 check (preflight_ok = 1)
);

insert into namewta_admin_runtime_reconcile_ddl_001_preflight (preflight_ok)
select if(
    @namewta_gen_table_rows = 0
    and @namewta_gen_column_rows = 0
    and not exists (
        select 1
        from information_schema.tables target_table
        left join information_schema.columns primary_column
          on primary_column.table_schema = target_table.table_schema
         and primary_column.table_name = target_table.table_name
         and primary_column.column_name = case target_table.table_name
             when 'gen_table' then 'table_id'
             when 'gen_table_column' then 'column_id'
         end
         and primary_column.column_key = 'PRI'
        where target_table.table_schema = database()
          and target_table.table_name in ('gen_table', 'gen_table_column')
          and (target_table.table_type <> 'BASE TABLE' or primary_column.column_name is null)
    ),
    1,
    0
);

drop table if exists gen_table_column;
drop table if exists gen_table;

drop temporary table namewta_admin_runtime_reconcile_ddl_001_preflight;

-- NAMEWTA-ADMIN-RUNTIME-RECONCILE-DDL-001-END
