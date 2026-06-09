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
