package com.example.transaction.workflow.engine;

/**
 * 任务类型（仅 TASK 节点有效）
 */
public enum TaskType {

    /** 人工任务 — 需要人处理，流程在此暂停等待 */
    HUMAN,

    /** 自动任务 — 由 Spring Bean 自动执行，流程继续推进 */
    AUTO
}
