-- ========================================
-- 事务演示数据库建表脚本
-- ========================================



-- 用户表（InnoDB 支持事务）
CREATE TABLE IF NOT EXISTS `t_user` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `username` VARCHAR(50) NOT NULL,
    `email` VARCHAR(100),
    `status` INT DEFAULT 1 COMMENT '1:正常 0:禁用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 账户表（用于转账等事务演示）
CREATE TABLE IF NOT EXISTS `t_account` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `balance` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '余额',
    `account_type` VARCHAR(20) DEFAULT 'SAVING' COMMENT '账户类型',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账户表';

-- 事务日志表（用于记录事务执行过程）
CREATE TABLE IF NOT EXISTS `t_transaction_log` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `operation` VARCHAR(100) NOT NULL COMMENT '操作描述',
    `status` VARCHAR(20) NOT NULL COMMENT 'SUCCESS/FAIL/ROLLBACK',
    `detail` TEXT COMMENT '详细信息',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事务日志表';

-- 演示用：隔离级别测试表
CREATE TABLE IF NOT EXISTS `t_isolation_test` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `name` VARCHAR(50) NOT NULL,
    `value` INT DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='隔离级别测试表';


-- ========================================
-- 通用流程引擎建表脚本
-- ========================================

-- 流程定义表（模板/蓝图）
CREATE TABLE IF NOT EXISTS `t_wf_process` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `process_key` VARCHAR(64) NOT NULL COMMENT '流程唯一标识',
    `process_name` VARCHAR(128) NOT NULL COMMENT '流程名称',
    `business_type` VARCHAR(64) NOT NULL COMMENT '绑定业务类型, e.g. order, leave',
    `description` VARCHAR(512) COMMENT '描述',
    `version` INT NOT NULL DEFAULT 1 COMMENT '版本号',
    `status` INT NOT NULL DEFAULT 0 COMMENT '0=草稿 1=已发布 2=已停用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_process_key` (`process_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程定义表';

-- 流程节点表
CREATE TABLE IF NOT EXISTS `t_wf_node` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `process_id` BIGINT NOT NULL COMMENT '所属流程定义ID',
    `node_key` VARCHAR(64) NOT NULL COMMENT '节点标识',
    `node_name` VARCHAR(128) NOT NULL COMMENT '节点名称',
    `node_type` VARCHAR(16) NOT NULL COMMENT 'START/END/TASK/GATEWAY',
    `task_type` VARCHAR(16) COMMENT 'HUMAN/AUTO, 仅TASK类型有效',
    `handler_bean` VARCHAR(128) COMMENT '自动任务处理器Bean名',
    `position_x` INT DEFAULT 0 COMMENT '画布X坐标',
    `position_y` INT DEFAULT 0 COMMENT '画布Y坐标',
    KEY `idx_process_id` (`process_id`),
    UNIQUE KEY `uk_process_node` (`process_id`, `node_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程节点表';

-- 流程连线表
CREATE TABLE IF NOT EXISTS `t_wf_transition` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `process_id` BIGINT NOT NULL COMMENT '所属流程定义ID',
    `trans_key` VARCHAR(64) NOT NULL COMMENT '连线标识',
    `trans_name` VARCHAR(128) COMMENT '连线名称',
    `source_node_id` BIGINT NOT NULL COMMENT '起始节点ID',
    `target_node_id` BIGINT NOT NULL COMMENT '目标节点ID',
    `condition_expr` TEXT COMMENT 'SpEL条件表达式',
    `sort_order` INT DEFAULT 0 COMMENT '优先级(小的优先)',
    KEY `idx_process_id` (`process_id`),
    KEY `idx_source_node` (`source_node_id`),
    UNIQUE KEY `uk_trans_key` (`process_id`, `trans_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程连线表';

-- 流程实例表（某次运行）
CREATE TABLE IF NOT EXISTS `t_wf_instance` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `process_id` BIGINT NOT NULL COMMENT '流程定义ID',
    `business_type` VARCHAR(64) NOT NULL COMMENT '业务类型',
    `business_id` VARCHAR(64) NOT NULL COMMENT '业务主键',
    `status` INT NOT NULL DEFAULT 0 COMMENT '0=运行中 1=已完成 2=已取消',
    `variables` JSON COMMENT '流程变量JSON',
    `start_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `end_time` DATETIME COMMENT '结束时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY `idx_process_id` (`process_id`),
    KEY `idx_business` (`business_type`, `business_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程实例表';

-- 流程实例节点表（当前状态 + 历史记录）
CREATE TABLE IF NOT EXISTS `t_wf_instance_node` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `instance_id` BIGINT NOT NULL COMMENT '流程实例ID',
    `node_id` BIGINT NOT NULL COMMENT '节点定义ID',
    `status` INT NOT NULL DEFAULT 0 COMMENT '0=待处理 1=进行中 2=已完成 3=已跳过',
    `operator` VARCHAR(64) COMMENT '处理人',
    `comment` VARCHAR(512) COMMENT '审批意见',
    `start_time` DATETIME COMMENT '开始处理时间',
    `end_time` DATETIME COMMENT '完成时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY `idx_instance_id` (`instance_id`),
    KEY `idx_node_id` (`node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程实例节点表';


-- ========================================
-- 权限管理系统建表脚本（RBAC 模型）
-- ========================================

-- 部门表（数据权限用）
CREATE TABLE IF NOT EXISTS `sys_dept` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父部门ID，0=顶级',
    `dept_name` VARCHAR(50) NOT NULL COMMENT '部门名称',
    `sort` INT DEFAULT 0 COMMENT '排序',
    `leader` VARCHAR(50) COMMENT '负责人',
    `phone` VARCHAR(20) COMMENT '联系电话',
    `email` VARCHAR(100) COMMENT '邮箱',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0=禁用 1=正常',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门表';

-- 用户表（权限系统专用，与业务 t_user 分离）
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(200) NOT NULL COMMENT 'BCrypt加密密码',
    `nickname` VARCHAR(50) COMMENT '昵称',
    `email` VARCHAR(100) COMMENT '邮箱',
    `phone` VARCHAR(20) COMMENT '手机号',
    `avatar` VARCHAR(500) COMMENT '头像URL',
    `dept_id` BIGINT COMMENT '所属部门ID',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0=禁用 1=正常',
    `login_ip` VARCHAR(50) COMMENT '最后登录IP',
    `login_time` DATETIME COMMENT '最后登录时间',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_dept_id` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- 角色表
CREATE TABLE IF NOT EXISTS `sys_role` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `role_name` VARCHAR(50) NOT NULL COMMENT '角色名称',
    `role_key` VARCHAR(50) NOT NULL COMMENT '角色标识（如 ROLE_ADMIN）',
    `data_scope` TINYINT NOT NULL DEFAULT 1 COMMENT '数据范围：1=全部 2=本部门 3=本部门及以下 4=仅本人',
    `sort` INT DEFAULT 0 COMMENT '排序',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0=禁用 1=正常',
    `remark` VARCHAR(500) COMMENT '备注',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_role_key` (`role_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 权限表（菜单/按钮/接口统一管理）
CREATE TABLE IF NOT EXISTS `sys_permission` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父权限ID，0=顶级',
    `name` VARCHAR(50) NOT NULL COMMENT '权限名称',
    `perm_key` VARCHAR(100) COMMENT '权限标识（如 sys:user:add）',
    `type` TINYINT NOT NULL COMMENT '1=目录 2=菜单 3=按钮 4=接口',
    `path` VARCHAR(200) COMMENT '路由路径（菜单用）',
    `component` VARCHAR(200) COMMENT '前端组件路径',
    `icon` VARCHAR(100) COMMENT '图标',
    `sort` INT DEFAULT 0 COMMENT '排序',
    `visible` TINYINT NOT NULL DEFAULT 1 COMMENT '0=隐藏 1=可见',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0=禁用 1=正常',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

-- 用户-角色关联表
CREATE TABLE IF NOT EXISTS `sys_user_role` (
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (`user_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- 角色-权限关联表
CREATE TABLE IF NOT EXISTS `sys_role_permission` (
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `permission_id` BIGINT NOT NULL COMMENT '权限ID',
    PRIMARY KEY (`role_id`, `permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

-- 操作日志表
CREATE TABLE IF NOT EXISTS `sys_oper_log` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `module` VARCHAR(50) COMMENT '操作模块',
    `description` VARCHAR(200) COMMENT '操作描述',
    `request_url` VARCHAR(500) COMMENT '请求URL',
    `request_method` VARCHAR(10) COMMENT 'HTTP方法',
    `request_params` TEXT COMMENT '请求参数',
    `response_result` TEXT COMMENT '返回结果',
    `status` TINYINT DEFAULT 1 COMMENT '0=失败 1=成功',
    `error_msg` VARCHAR(2000) COMMENT '错误信息',
    `oper_user_id` BIGINT COMMENT '操作人ID',
    `oper_username` VARCHAR(50) COMMENT '操作人用户名',
    `oper_ip` VARCHAR(50) COMMENT '操作IP',
    `oper_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    `cost_time` BIGINT COMMENT '耗时(ms)',
    KEY `idx_oper_time` (`oper_time`),
    KEY `idx_oper_user` (`oper_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';
