package com.example.transaction.workflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流程节点 — 流程中的一个步骤
 *
 * 节点类型（nodeType）：
 * - START:   开始节点，每个流程恰好 1 个
 * - END:     结束节点，1 个或多个
 * - TASK:    任务节点，分为人工(HUMAN)和自动(AUTO)
 * - GATEWAY: 网关节点，条件分支
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "t_wf_node")
public class WfNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属流程定义 ID */
    @Column(name = "process_id", nullable = false)
    private Long processId;

    /** 节点标识，如 "start", "manager_review" */
    @Column(name = "node_key", nullable = false, length = 64)
    private String nodeKey;

    /** 节点名称，如 "主管审批" */
    @Column(name = "node_name", nullable = false, length = 128)
    private String nodeName;

    /** 节点类型：START / END / TASK / GATEWAY */
    @Column(name = "node_type", nullable = false, length = 16)
    private String nodeType;

    /** 任务类型：HUMAN / AUTO（仅 TASK 类型有效） */
    @Column(name = "task_type", length = 16)
    private String taskType;

    /** 自动任务处理器 Spring Bean 名（仅 TASK + AUTO 有效） */
    @Column(name = "handler_bean", length = 128)
    private String handlerBean;

    /** 画布 X 坐标（前端可视化用） */
    @Column(name = "position_x")
    private Integer positionX;

    /** 画布 Y 坐标（前端可视化用） */
    @Column(name = "position_y")
    private Integer positionY;
}
