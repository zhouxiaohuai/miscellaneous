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


-- ========================================
-- 权限管理系统初始数据
-- ========================================

-- 部门数据
INSERT IGNORE INTO `sys_dept` (`id`, `parent_id`, `dept_name`, `sort`, `leader`) VALUES
(1, 0, '总公司', 1, '管理员'),
(2, 1, '技术部', 1, '张三'),
(3, 1, '运营部', 2, '李四'),
(4, 1, '财务部', 3, '王五');

-- 用户数据（密码均为 BCrypt 加密的 "123456"）
-- BCrypt: $2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi
INSERT IGNORE INTO `sys_user` (`id`, `username`, `password`, `nickname`, `email`, `dept_id`, `status`) VALUES
(1, 'admin',  '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '超级管理员', 'admin@example.com', 1, 1),
(2, 'zhangsan', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '张三', 'zhangsan@example.com', 2, 1),
(3, 'lisi',    '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '李四', 'lisi@example.com', 3, 1);

-- 角色数据
INSERT IGNORE INTO `sys_role` (`id`, `role_name`, `role_key`, `data_scope`, `sort`, `remark`) VALUES
(1, '超级管理员', 'ROLE_SUPER_ADMIN', 1, 1, '拥有所有权限，不受数据权限限制'),
(2, '系统管理员', 'ROLE_ADMIN', 1, 2, '系统管理权限'),
(3, '运营管理员', 'ROLE_OPS_ADMIN', 2, 3, '运营相关权限，仅看本部门数据'),
(4, '普通用户',   'ROLE_USER', 4, 4, '基础权限，仅看本人数据');

-- 用户-角色关联
INSERT IGNORE INTO `sys_user_role` (`user_id`, `role_id`) VALUES
(1, 1),  -- admin → 超级管理员
(2, 3),  -- zhangsan → 运营管理员
(3, 4);  -- lisi → 普通用户

-- 权限数据（菜单/按钮/接口）
-- type: 1=目录 2=菜单 3=按钮 4=接口

-- 系统管理目录
INSERT IGNORE INTO `sys_permission` (`id`, `parent_id`, `name`, `perm_key`, `type`, `path`, `component`, `icon`, `sort`) VALUES
(1,  0,  '系统管理',   NULL,                1, '/system',       NULL,                     'setting',  1),
(2,  1,  '用户管理',   NULL,                2, '/system/user',  'system/user/index',      'user',     1),
(3,  1,  '角色管理',   NULL,                2, '/system/role',  'system/role/index',      'peoples',  2),
(4,  1,  '菜单管理',   NULL,                2, '/system/menu',  'system/menu/index',      'tree-table', 3),
(5,  1,  '部门管理',   NULL,                2, '/system/dept',  'system/dept/index',      'tree',     4);

-- 用户管理按钮权限
INSERT IGNORE INTO `sys_permission` (`id`, `parent_id`, `name`, `perm_key`, `type`, `sort`) VALUES
(10, 2, '用户查询', 'sys:user:list',    3, 1),
(11, 2, '用户新增', 'sys:user:add',     3, 2),
(12, 2, '用户修改', 'sys:user:edit',    3, 3),
(13, 2, '用户删除', 'sys:user:delete',  3, 4),
(14, 2, '重置密码', 'sys:user:reset-pwd', 3, 5);

-- 角色管理按钮权限
INSERT IGNORE INTO `sys_permission` (`id`, `parent_id`, `name`, `perm_key`, `type`, `sort`) VALUES
(20, 3, '角色查询', 'sys:role:list',    3, 1),
(21, 3, '角色新增', 'sys:role:add',     3, 2),
(22, 3, '角色修改', 'sys:role:edit',    3, 3),
(23, 3, '角色删除', 'sys:role:delete',  3, 4),
(24, 3, '分配权限', 'sys:role:assign',  3, 5);

-- 菜单管理按钮权限
INSERT IGNORE INTO `sys_permission` (`id`, `parent_id`, `name`, `perm_key`, `type`, `sort`) VALUES
(30, 4, '菜单查询', 'sys:perm:list',    3, 1),
(31, 4, '菜单新增', 'sys:perm:add',     3, 2),
(32, 4, '菜单修改', 'sys:perm:edit',    3, 3),
(33, 4, '菜单删除', 'sys:perm:delete',  3, 4);

-- 部门管理按钮权限
INSERT IGNORE INTO `sys_permission` (`id`, `parent_id`, `name`, `perm_key`, `type`, `sort`) VALUES
(40, 5, '部门查询', 'sys:dept:list',    3, 1),
(41, 5, '部门新增', 'sys:dept:add',     3, 2),
(42, 5, '部门修改', 'sys:dept:edit',    3, 3),
(43, 5, '部门删除', 'sys:dept:delete',  3, 4);

-- 接口权限（对应后端 API 路径）
INSERT IGNORE INTO `sys_permission` (`id`, `parent_id`, `name`, `perm_key`, `type`, `sort`) VALUES
(100, 0, '认证接口',     'auth',              4, 1),
(101, 0, '用户管理接口', 'sys:user:api',      4, 2),
(102, 0, '角色管理接口', 'sys:role:api',      4, 3),
(103, 0, '权限管理接口', 'sys:perm:api',      4, 4),
(104, 0, '部门管理接口', 'sys:dept:api',      4, 5);

-- 角色-权限关联（超级管理员拥有所有权限）
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES
(1, 1),  (1, 2),  (1, 3),  (1, 4),  (1, 5),
(1, 10), (1, 11), (1, 12), (1, 13), (1, 14),
(1, 20), (1, 21), (1, 22), (1, 23), (1, 24),
(1, 30), (1, 31), (1, 32), (1, 33),
(1, 40), (1, 41), (1, 42), (1, 43),
(1, 100),(1, 101),(1, 102),(1, 103),(1, 104);

-- 运营管理员权限（用户查询、角色查询）
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES
(3, 1), (3, 2), (3, 10), (3, 3), (3, 20);

-- 普通用户权限（仅查看）
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES
(4, 1), (4, 2), (4, 10);
