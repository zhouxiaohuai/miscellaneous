package com.example.transaction.workflow.engine;

import com.example.transaction.workflow.entity.WfInstance;
import com.example.transaction.workflow.entity.WfNode;

/**
 * 自动任务处理器接口
 *
 * 实现此接口并注册为 Spring Bean，将 Bean 名称设置到 WfNode.handlerBean。
 * 引擎执行到 AUTO 类型的 TASK 节点时，会自动调用 execute() 方法。
 *
 * 示例：
 * <pre>
 * @Component("orderCreateHandler")
 * public class OrderCreateHandler implements AutoTaskHandler {
 *     @Override
 *     public void execute(WfInstance instance, WfNode node) {
 *         // 自动创建订单逻辑
 *     }
 * }
 * </pre>
 */
public interface AutoTaskHandler {

    /**
     * 执行自动任务
     *
     * @param instance 当前流程实例（包含流程变量）
     * @param node     当前任务节点定义
     */
    void execute(WfInstance instance, WfNode node);
}
