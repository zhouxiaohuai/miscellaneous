package com.example.transaction.workflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 流程实例节点 — 记录每个节点的执行状态
 *
 * 既是"当前状态"也是"历史记录"：
 * - 进行中(status=1)的节点 = 当前正在处理的节点
 * - 已完成(status=2)的节点 = 历史记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "t_wf_instance_node")
public class WfInstanceNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 流程实例 ID */
    @Column(name = "instance_id", nullable = false)
    private Long instanceId;

    /** 节点定义 ID */
    @Column(name = "node_id", nullable = false)
    private Long nodeId;

    /** 状态：0=待处理, 1=进行中, 2=已完成, 3=已跳过 */
    @Column(nullable = false)
    private Integer status;

    /** 处理人 */
    @Column(length = 64)
    private String operator;

    /** 审批意见 */
    @Column(length = 512)
    private String comment;

    /** 开始处理时间 */
    @Column(name = "start_time")
    private LocalDateTime startTime;

    /** 完成时间 */
    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    public void prePersist() {
        this.createTime = LocalDateTime.now();
    }
}
