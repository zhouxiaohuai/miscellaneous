package com.example.transaction.workflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流程连线 — 节点之间的连接
 *
 * 连线可以设置 SpEL 条件表达式，用于网关分支判断。
 * 条件为空/null 表示无条件通过（默认路径）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "t_wf_transition")
public class WfTransition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属流程定义 ID */
    @Column(name = "process_id", nullable = false)
    private Long processId;

    /** 连线标识，如 "t1", "to_approve" */
    @Column(name = "trans_key", nullable = false, length = 64)
    private String transKey;

    /** 连线名称，如 "金额>1000走大额审批" */
    @Column(name = "trans_name", length = 128)
    private String transName;

    /** 起始节点 ID */
    @Column(name = "source_node_id", nullable = false)
    private Long sourceNodeId;

    /** 目标节点 ID */
    @Column(name = "target_node_id", nullable = false)
    private Long targetNodeId;

    /** SpEL 条件表达式，如 "#amount > 1000"，null/空 = 无条件 */
    @Column(name = "condition_expr", columnDefinition = "TEXT")
    private String conditionExpr;

    /** 优先级排序（小的优先），同一 source 下按此顺序评估 */
    @Column(name = "sort_order")
    private Integer sortOrder;
}
