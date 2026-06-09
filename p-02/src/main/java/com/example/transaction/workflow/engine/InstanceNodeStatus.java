package com.example.transaction.workflow.engine;

/**
 * 流程实例节点状态
 */
public enum InstanceNodeStatus {

    /** 待处理 */
    PENDING(0),

    /** 进行中（当前活跃节点） */
    ACTIVE(1),

    /** 已完成 */
    COMPLETED(2),

    /** 已跳过（流程取消时） */
    SKIPPED(3);

    private final int code;

    InstanceNodeStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static InstanceNodeStatus fromCode(int code) {
        for (InstanceNodeStatus s : values()) {
            if (s.code == code) return s;
        }
        throw new IllegalArgumentException("未知的实例节点状态码: " + code);
    }
}
