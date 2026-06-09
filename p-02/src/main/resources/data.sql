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


-- ========================================
-- 流程引擎示例数据：订单审批流程
-- ========================================

-- 流程定义
INSERT IGNORE INTO `t_wf_process` (`id`, `process_key`, `process_name`, `business_type`, `description`, `version`, `status`) VALUES
(1, 'order_approval', '订单审批流程', 'order', '订单金额>1000需要总监审批，否则主管审批即可', 1, 1);

-- 流程节点
INSERT IGNORE INTO `t_wf_node` (`id`, `process_id`, `node_key`, `node_name`, `node_type`, `task_type`, `handler_bean`, `position_x`, `position_y`) VALUES
(1, 1, 'start',           '开始',         'START',   NULL,    NULL,                       0,    100),
(2, 1, 'create_order',    '创建订单',     'TASK',    'AUTO',  'orderCreateHandler',       200,  100),
(3, 1, 'manager_review',  '主管审批',     'TASK',    'HUMAN', NULL,                       400,  100),
(4, 1, 'decision',        '金额判断',     'GATEWAY', NULL,    NULL,                       600,  100),
(5, 1, 'director_review', '总监审批',     'TASK',    'HUMAN', NULL,                       800,  0),
(6, 1, 'process_payment', '处理支付',     'TASK',    'AUTO',  'paymentHandler',           1000, 0),
(7, 1, 'end_success',     '完成',         'END',     NULL,    NULL,                       1200, 0),
(8, 1, 'rejected_notify', '拒绝通知',     'TASK',    'AUTO',  'rejectionNotifyHandler',   800,  200),
(9, 1, 'end_rejected',    '已拒绝',       'END',     NULL,    NULL,                       1000, 200);

-- 流程连线
INSERT IGNORE INTO `t_wf_transition` (`id`, `process_id`, `trans_key`, `trans_name`, `source_node_id`, `target_node_id`, `condition_expr`, `sort_order`) VALUES
(1,  1, 't1', '开始→创建订单',     1, 2, NULL, 0),
(2,  1, 't2', '创建订单→主管审批', 2, 3, NULL, 0),
(3,  1, 't3', '主管审批→金额判断', 3, 4, NULL, 0),
(4,  1, 't4', '金额>1000且已批准',  4, 5, '#amount > 1000 and #approved == true', 1),
(5,  1, 't5', '默认→拒绝通知',     4, 8, NULL, 2),
(6,  1, 't6', '总监批准→处理支付', 5, 6, '#approved == true', 0),
(7,  1, 't7', '总监拒绝→拒绝通知', 5, 8, '#approved == false', 0),
(8,  1, 't8', '处理支付→完成',     6, 7, NULL, 0),
(9,  1, 't9', '拒绝通知→已拒绝',   8, 9, NULL, 0);
