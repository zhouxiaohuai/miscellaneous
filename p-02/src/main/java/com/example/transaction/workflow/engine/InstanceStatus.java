package com.example.transaction.workflow.engine;

/**
 * 流程实例状态
 */
public enum InstanceStatus {

    /** 运行中 */
    RUNNING(0),

    /** 已完成 */
    COMPLETED(1),

    /** 已取消 */
    CANCELLED(2);

    private final int code;

    InstanceStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static InstanceStatus fromCode(int code) {
        for (InstanceStatus s : values()) {
            if (s.code == code) return s;
        }
        throw new IllegalArgumentException("未知的实例状态码: " + code);
    }
}
