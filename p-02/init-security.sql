-- ========================================
-- 权限管理系统初始化脚本
-- 密码: 123456 (BCrypt加密)
-- ========================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 清空旧数据（按外键依赖顺序）
DELETE FROM `sys_role_permission`;
DELETE FROM `sys_user_role`;
DELETE FROM `sys_oper_log`;
DELETE FROM `sys_permission`;
DELETE FROM `sys_role`;
DELETE FROM `sys_user`;
DELETE FROM `sys_dept`;

-- 重置自增ID
ALTER TABLE `sys_dept` AUTO_INCREMENT = 1;
ALTER TABLE `sys_user` AUTO_INCREMENT = 1;
ALTER TABLE `sys_role` AUTO_INCREMENT = 1;
ALTER TABLE `sys_permission` AUTO_INCREMENT = 1;
ALTER TABLE `sys_oper_log` AUTO_INCREMENT = 1;

-- ========================================
-- 部门数据（树形结构）
-- ========================================
-- 总公司(1)
--   ├── 技术部(2)
--   │     ├── 后端组(5)
--   │     └── 前端组(6)
--   ├── 运营部(3)
--   └── 财务部(4)

INSERT INTO `sys_dept` (`id`, `parent_id`, `dept_name`, `sort`, `leader`) VALUES
(1, 0, '总公司',  1, '管理员'),
(2, 1, '技术部',  1, '张三'),
(3, 1, '运营部',  2, '李四'),
(4, 1, '财务部',  3, '王五'),
(5, 2, '后端组',  1, '赵六'),
(6, 2, '前端组',  2, '钱七');

-- ========================================
-- 用户数据（密码均为 123456）
-- BCrypt: $2a$10$oxQRCwQIXZXxTna2kC/OZuXVafffUMMgSwuzAgOneRuUWMpnQuWOW
-- ========================================

INSERT INTO `sys_user` (`id`, `username`, `password`, `nickname`, `email`, `dept_id`, `status`) VALUES
(1, 'admin',    '$2a$10$oxQRCwQIXZXxTna2kC/OZuXVafffUMMgSwuzAgOneRuUWMpnQuWOW', '超级管理员', 'admin@example.com',    1, 1),
(2, 'zhangsan', '$2a$10$oxQRCwQIXZXxTna2kC/OZuXVafffUMMgSwuzAgOneRuUWMpnQuWOW', '张三',       'zhangsan@example.com', 2, 1),
(3, 'lisi',     '$2a$10$oxQRCwQIXZXxTna2kC/OZuXVafffUMMgSwuzAgOneRuUWMpnQuWOW', '李四',       'lisi@example.com',     3, 1),
(4, 'wangwu',   '$2a$10$oxQRCwQIXZXxTna2kC/OZuXVafffUMMgSwuzAgOneRuUWMpnQuWOW', '王五',       'wangwu@example.com',   5, 1),
(5, 'zhaoliu',  '$2a$10$oxQRCwQIXZXxTna2kC/OZuXVafffUMMgSwuzAgOneRuUWMpnQuWOW', '赵六',       'zhaoliu@example.com',  6, 0);

-- ========================================
-- 角色数据
-- ========================================
-- data_scope: 1=全部 2=本部门 3=本部门及下级 4=仅本人

INSERT INTO `sys_role` (`id`, `role_name`, `role_key`, `data_scope`, `sort`, `remark`) VALUES
(1, '超级管理员', 'ROLE_SUPER_ADMIN', 1, 1, '拥有所有权限，不受数据权限限制'),
(2, '系统管理员', 'ROLE_ADMIN',       1, 2, '系统管理权限，全部数据'),
(3, '运营管理员', 'ROLE_OPS_ADMIN',   2, 3, '运营相关权限，仅看本部门数据'),
(4, '普通用户',   'ROLE_USER',        4, 4, '基础权限，仅看本人数据'),
(5, '部门主管',   'ROLE_DEPT_MGR',    3, 5, '本部门及下级部门数据');

-- ========================================
-- 用户-角色关联
-- ========================================
-- admin(总公司)      → 超级管理员(全部数据)
-- zhangsan(技术部)   → 部门主管(本部门及下级)
-- lisi(运营部)       → 普通用户(仅本人)
-- wangwu(后端组)     → 运营管理员(本部门)
-- zhaoliu(前端组)    → 普通用户(禁用状态)

INSERT INTO `sys_user_role` (`user_id`, `role_id`) VALUES
(1, 1),
(2, 5),
(3, 4),
(4, 3),
(5, 4);

-- ========================================
-- 权限数据（菜单/按钮/接口）
-- type: 1=目录 2=菜单 3=按钮 4=接口
-- ========================================

-- 一级目录
INSERT INTO `sys_permission` (`id`, `parent_id`, `name`, `perm_key`, `type`, `path`, `component`, `icon`, `sort`) VALUES
(1,  0,  '系统管理',  NULL,  1, '/system',      NULL,                'setting',  1);

-- 二级菜单
INSERT INTO `sys_permission` (`id`, `parent_id`, `name`, `perm_key`, `type`, `path`, `component`, `icon`, `sort`) VALUES
(2,  1,  '用户管理',  NULL,  2, '/system/user', 'system/user/index', 'user',     1),
(3,  1,  '角色管理',  NULL,  2, '/system/role', 'system/role/index', 'peoples',  2),
(4,  1,  '菜单管理',  NULL,  2, '/system/menu', 'system/menu/index', 'tree-table',3),
(5,  1,  '部门管理',  NULL,  2, '/system/dept', 'system/dept/index', 'tree',     4),
(6,  1,  '操作日志',  NULL,  2, '/system/log',  'system/log/index',  'log',      5);

-- 用户管理按钮
INSERT INTO `sys_permission` (`id`, `parent_id`, `name`, `perm_key`, `type`, `sort`) VALUES
(10, 2, '用户查询',  'sys:user:list',      3, 1),
(11, 2, '用户新增',  'sys:user:add',       3, 2),
(12, 2, '用户修改',  'sys:user:edit',      3, 3),
(13, 2, '用户删除',  'sys:user:delete',    3, 4),
(14, 2, '重置密码',  'sys:user:reset-pwd', 3, 5);

-- 角色管理按钮
INSERT INTO `sys_permission` (`id`, `parent_id`, `name`, `perm_key`, `type`, `sort`) VALUES
(20, 3, '角色查询',  'sys:role:list',      3, 1),
(21, 3, '角色新增',  'sys:role:add',       3, 2),
(22, 3, '角色修改',  'sys:role:edit',      3, 3),
(23, 3, '角色删除',  'sys:role:delete',    3, 4),
(24, 3, '分配权限',  'sys:role:assign',    3, 5);

-- 菜单管理按钮
INSERT INTO `sys_permission` (`id`, `parent_id`, `name`, `perm_key`, `type`, `sort`) VALUES
(30, 4, '菜单查询',  'sys:perm:list',      3, 1),
(31, 4, '菜单新增',  'sys:perm:add',       3, 2),
(32, 4, '菜单修改',  'sys:perm:edit',      3, 3),
(33, 4, '菜单删除',  'sys:perm:delete',    3, 4);

-- 部门管理按钮
INSERT INTO `sys_permission` (`id`, `parent_id`, `name`, `perm_key`, `type`, `sort`) VALUES
(40, 5, '部门查询',  'sys:dept:list',      3, 1),
(41, 5, '部门新增',  'sys:dept:add',       3, 2),
(42, 5, '部门修改',  'sys:dept:edit',      3, 3),
(43, 5, '部门删除',  'sys:dept:delete',    3, 4);

-- 日志查看按钮
INSERT INTO `sys_permission` (`id`, `parent_id`, `name`, `perm_key`, `type`, `sort`) VALUES
(50, 6, '日志查询',  'sys:log:list',       3, 1),
(51, 6, '日志删除',  'sys:log:delete',     3, 2);

-- ========================================
-- 角色-权限关联
-- ========================================

-- 超级管理员：全部权限
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES
(1,1),(1,2),(1,3),(1,4),(1,5),(1,6),
(1,10),(1,11),(1,12),(1,13),(1,14),
(1,20),(1,21),(1,22),(1,23),(1,24),
(1,30),(1,31),(1,32),(1,33),
(1,40),(1,41),(1,42),(1,43),
(1,50),(1,51);

-- 系统管理员：用户+角色+菜单+部门管理
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES
(2,1),(2,2),(2,3),(2,4),(2,5),(2,6),
(2,10),(2,11),(2,12),(2,13),(2,14),
(2,20),(2,21),(2,22),(2,23),(2,24),
(2,30),(2,31),(2,32),(2,33),
(2,40),(2,41),(2,42),(2,43),
(2,50);

-- 部门主管：用户查询+部门查询+日志查看
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES
(5,1),(5,2),(5,10),(5,5),(5,40),(5,6),(5,50);

-- 运营管理员：用户查询+角色查询
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES
(3,1),(3,2),(3,10),(3,3),(3,20);

-- 普通用户：仅查看
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES
(4,1),(4,2),(4,10);

SELECT '=== 权限系统初始化完成 ===' AS result;
