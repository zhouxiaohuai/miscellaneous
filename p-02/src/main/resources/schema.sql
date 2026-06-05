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
