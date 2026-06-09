package com.example.transaction.workflow.engine;

import com.example.transaction.workflow.entity.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 流程状态 DTO — 返回给前端的完整状态信息
 *
 * 包含：实例信息、当前活跃节点、历史记录、完整流程定义（用于前端可视化）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowStatusDTO {

    /** 流程实例 */
    private WfInstance instance;

    /** 流程定义 */
    private WfProcess process;

    /** 当前活跃节点（可能有多个，并行场景） */
    private List<WfInstanceNode> activeNodes;

    /** 历史节点（已完成/已跳过） */
    private List<WfInstanceNode> historyNodes;

    /** 所有节点定义（用于前端画图） */
    private List<WfNode> allNodes;

    /** 所有连线定义（用于前端画图） */
    private List<WfTransition> allTransitions;
}
