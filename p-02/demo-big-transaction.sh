#!/bin/bash

# ========================================
# 大事务处理演示脚本
# ========================================
#
# 演示内容：
# 1. 大事务问题演示（反模式）
# 2. 分片提交策略
# 3. 超时控制策略
# 4. 异步处理策略
# 5. 生产环境优化建议
#
# 使用方法：
# 1. 启动应用：mvn spring-boot:run
# 2. 运行此脚本：bash demo-big-transaction.sh

BASE_URL="http://localhost:8080/api/big-tx-demo"

echo "========================================"
echo "大事务处理完整演示"
echo "========================================"
echo ""

# 等待应用启动
echo "等待应用启动..."
sleep 5

# 1. 大事务问题演示
echo ""
echo "=== 1. 大事务问题演示（反模式） ==="
echo "演示大事务的三大问题："
echo "1. 事务中包含远程调用 → 连接被白白占用"
echo "2. N+1循环查询 → 事务时间线性增长"
echo "3. 全方法事务 → 查询也占用事务时间"
echo ""
curl -s "$BASE_URL/problem-demo?userId=1" | python -m json.tool
echo ""
echo "按 Enter 继续..."
read

# 2. 分片提交策略演示
echo ""
echo "=== 2. 分片提交策略演示 ==="
echo "将1000条数据按每100条分片提交"
echo "优势：每个事务只持有连接很短时间，某批失败只回滚当前批次"
echo ""
curl -s "$BASE_URL/chunked-commit?totalCount=1000&chunkSize=100" | python -m json.tool
echo ""
echo "按 Enter 继续..."
read

# 3. 超时控制策略演示
echo ""
echo "=== 3. 超时控制策略演示 ==="
echo "演示事务超时控制：3秒超时，模拟5秒耗时"
echo "预期：事务超时回滚"
echo ""
curl -s "$BASE_URL/timeout-control?userId=1&workSeconds=5" | python -m json.tool
echo ""
echo "按 Enter 继续..."
read

# 4. 异步处理策略演示
echo ""
echo "=== 4. 异步处理策略演示 ==="
echo "演示非核心操作异步处理"
echo "对比：同步全包 vs 异步非核心"
echo ""
curl -s "$BASE_URL/async-processing?userId=1" | python -m json.tool
echo ""
echo "按 Enter 继续..."
read

# 5. 生产环境优化建议
echo ""
echo "=== 5. 生产环境优化建议 ==="
echo "生产环境大事务优化的完整建议"
echo ""
curl -s "$BASE_URL/production-optimization" | python -m json.tool
echo ""
echo "按 Enter 继续..."
read

# 6. 综合对比演示
echo ""
echo "=== 6. 综合对比演示 ==="
echo "对比三种实现：单大事务 vs 分片提交 vs 每条单独事务"
echo ""
curl -s "$BASE_URL/comprehensive-comparison?userCount=1000" | python -m json.tool
echo ""
echo "按 Enter 继续..."
read

# 7. 大事务处理完整流程演示
echo ""
echo "=== 7. 大事务处理完整流程演示 ==="
echo "模拟完整的业务场景：用户扣款操作"
echo "展示如何将大事务拆分成多个小步骤"
echo ""
curl -s "$BASE_URL/complete-flow?userId=1&amount=1000" | python -m json.tool
echo ""

echo "========================================"
echo "演示完成！"
echo "========================================"
echo ""
echo "总结："
echo "1. 大事务问题：连接占用、锁竞争、Undo Log膨胀"
echo "2. 分片提交：将大事务拆成小事务，每片独立提交"
echo "3. 超时控制：三层超时防护，防止事务无限期持有"
echo "4. 异步处理：非核心操作异步执行，缩短事务时间"
echo "5. 生产优化：代码、数据库、连接池、监控告警"
echo ""
echo "关键原则："
echo "- 事务最小化：只在真正需要事务的方法上加@Transactional"
echo "- 远程调用外提：RPC/HTTP/文件操作必须在事务外完成"
echo "- 批量操作优化：使用批量查询+批量更新，减少SQL次数"
echo "- 非核心异步：日志、通知等非核心操作异步执行"
echo "- 延迟加锁：SELECT FOR UPDATE 尽量靠近更新操作"
