-- ========================================
-- 初始数据
-- ========================================

-- 用户数据
INSERT IGNORE INTO `t_user` (`id`, `username`, `email`, `status`) VALUES
(1, 'zhangsan', 'zhangsan@example.com', 1),
(2, 'lisi', 'lisi@example.com', 1),
(3, 'wangwu', 'wangwu@example.com', 1),
(4, 'zhaoliu', 'zhaoliu@example.com', 1);

-- 账户数据
INSERT IGNORE INTO `t_account` (`id`, `user_id`, `balance`, `account_type`) VALUES
(1, 1, 10000.00, 'SAVING'),
(2, 2, 5000.00, 'SAVING'),
(3, 3, 8000.00, 'SAVING'),
(4, 4, 3000.00, 'SAVING');

-- 隔离级别测试初始数据
INSERT IGNORE INTO `t_isolation_test` (`id`, `name`, `value`) VALUES
(1, 'test_key', 100);
