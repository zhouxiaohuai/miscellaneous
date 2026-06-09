package com.example.transaction.workflow.engine;

/**
 * 节点类型
 */
public enum NodeType {

    /** 开始节点 — 每个流程恰好 1 个，自动通过 */
    START,

    /** 结束节点 — 1 个或多个，标记流程完成 */
    END,

    /** 任务节点 — 人工(HUMAN)或自动(AUTO) */
    TASK,

    /** 网关节点 — 条件分支，按 SpEL 表达式选择路径 */
    GATEWAY
}
