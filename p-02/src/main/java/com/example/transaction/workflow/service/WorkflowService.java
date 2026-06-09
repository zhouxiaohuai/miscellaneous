package com.example.transaction.workflow.service;

import com.example.transaction.workflow.engine.*;
import com.example.transaction.workflow.entity.*;
import com.example.transaction.workflow.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 流程服务 — 业务层，封装流程定义 CRUD + 实例管理 + 校验逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowEngine engine;
    private final WfProcessRepository processRepository;
    private final WfNodeRepository nodeRepository;
    private final WfTransitionRepository transitionRepository;
    private final WfInstanceRepository instanceRepository;
    private final WfInstanceNodeRepository instanceNodeRepository;

    // ========================
    // 流程定义 CRUD
    // ========================

    /**
     * 创建流程定义（含节点和连线）
     * 请求中 transitions 使用 nodeKey 引用节点，此处解析为 nodeId
     */
    @Transactional(rollbackFor = Exception.class)
    public WfProcess createProcess(WfProcess process, List<WfNode> nodes, List<WfTransition> transitions) {
        log.info("[流程服务] 创建流程定义: key={}, name={}", process.getProcessKey(), process.getProcessName());

        // 校验 processKey 唯一
        String processKey = process.getProcessKey();
        if (processRepository.findByProcessKey(processKey).isPresent()) {
            throw new WorkflowException("流程标识已存在: " + processKey);
        }

        // 保存流程定义
        process.setStatus(0); // 草稿
        process.setVersion(1);
        process = processRepository.save(process);

        // 保存节点
        Map<String, WfNode> nodeKeyMap = new HashMap<>();
        for (WfNode node : nodes) {
            node.setProcessId(process.getId());
            node = nodeRepository.save(node);
            nodeKeyMap.put(node.getNodeKey(), node);
        }

        // 保存连线（transitions 中的 sourceNodeId/targetNodeId 需要从 nodeKey 解析）
        // 这里直接使用传入的 transitions，它们的 sourceNodeId/targetNodeId 已经由前端或调用方设置
        // 如果使用 sourceNodeKey/targetNodeKey，需要解析
        for (WfTransition trans : transitions) {
            trans.setProcessId(process.getId());
            transitionRepository.save(trans);
        }

        // 设置关联数据
        process.setNodes(nodes);
        process.setTransitions(transitions);

        log.info("[流程服务] 流程定义创建成功: id={}, nodes={}, transitions={}",
                process.getId(), nodes.size(), transitions.size());
        return process;
    }

    /**
     * 创建流程定义（简化版：使用 nodeKey 引用）
     * 前端传 sourceNodeKey/targetNodeKey，后端解析为 nodeId
     */
    @Transactional(rollbackFor = Exception.class)
    public WfProcess createProcessFromKeys(WfProcess process, List<WfNode> nodes,
                                            List<Map<String, Object>> transitionMaps) {
        log.info("[流程服务] 创建流程定义（nodeKey 模式）: key={}", process.getProcessKey());

        // 校验 processKey 唯一
        String processKey = process.getProcessKey();
        if (processRepository.findByProcessKey(processKey).isPresent()) {
            throw new WorkflowException("流程标识已存在: " + processKey);
        }

        // 保存流程定义
        process.setStatus(0);
        process.setVersion(1);
        process = processRepository.save(process);

        // 保存节点，建立 nodeKey → node 映射
        Map<String, WfNode> nodeKeyMap = new HashMap<>();
        List<WfNode> savedNodes = new ArrayList<>();
        for (WfNode node : nodes) {
            node.setProcessId(process.getId());
            node = nodeRepository.save(node);
            nodeKeyMap.put(node.getNodeKey(), node);
            savedNodes.add(node);
        }

        // 解析并保存连线
        List<WfTransition> savedTransitions = new ArrayList<>();
        for (Map<String, Object> tMap : transitionMaps) {
            String sourceKey = (String) tMap.get("sourceNodeKey");
            String targetKey = (String) tMap.get("targetNodeKey");

            WfNode sourceNode = nodeKeyMap.get(sourceKey);
            WfNode targetNode = nodeKeyMap.get(targetKey);

            if (sourceNode == null) {
                throw new WorkflowException("连线的起始节点不存在: " + sourceKey);
            }
            if (targetNode == null) {
                throw new WorkflowException("连线的目标节点不存在: " + targetKey);
            }

            WfTransition trans = WfTransition.builder()
                    .processId(process.getId())
                    .transKey((String) tMap.get("transKey"))
                    .transName((String) tMap.get("transName"))
                    .sourceNodeId(sourceNode.getId())
                    .targetNodeId(targetNode.getId())
                    .conditionExpr((String) tMap.get("conditionExpr"))
                    .sortOrder(tMap.containsKey("sortOrder") ? (Integer) tMap.get("sortOrder") : 0)
                    .build();
            trans = transitionRepository.save(trans);
            savedTransitions.add(trans);
        }

        process.setNodes(savedNodes);
        process.setTransitions(savedTransitions);

        log.info("[流程服务] 流程定义创建成功: id={}, nodes={}, transitions={}",
                process.getId(), savedNodes.size(), savedTransitions.size());
        return process;
    }

    /**
     * 发布流程（草稿 → 已发布）
     * 发布前校验流程定义的完整性
     */
    @Transactional(rollbackFor = Exception.class)
    public WfProcess publishProcess(Long processId) {
        WfProcess process = processRepository.findById(processId)
                .orElseThrow(() -> new WorkflowException("流程定义不存在: " + processId));

        if (process.getStatus() != 0) {
            throw new WorkflowException("只有草稿状态的流程才能发布，当前状态: " + process.getStatus());
        }

        // 校验流程完整性
        validateProcessDefinition(processId);

        process.setStatus(1); // 已发布
        processRepository.save(process);

        log.info("[流程服务] 流程已发布: id={}, key={}", process.getId(), process.getProcessKey());
        return process;
    }

    /**
     * 停用流程（已发布 → 已停用）
     */
    @Transactional(rollbackFor = Exception.class)
    public WfProcess stopProcess(Long processId) {
        WfProcess process = processRepository.findById(processId)
                .orElseThrow(() -> new WorkflowException("流程定义不存在: " + processId));

        if (process.getStatus() != 1) {
            throw new WorkflowException("只有已发布的流程才能停用，当前状态: " + process.getStatus());
        }

        process.setStatus(2); // 已停用
        processRepository.save(process);

        log.info("[流程服务] 流程已停用: id={}, key={}", process.getId(), process.getProcessKey());
        return process;
    }

    /**
     * 获取流程定义详情（含节点和连线）
     */
    @Transactional(readOnly = true)
    public WfProcess getProcessDetail(Long processId) {
        WfProcess process = processRepository.findById(processId)
                .orElseThrow(() -> new WorkflowException("流程定义不存在: " + processId));
        process.setNodes(nodeRepository.findByProcessId(processId));
        process.setTransitions(transitionRepository.findByProcessId(processId));
        return process;
    }

    /**
     * 列出流程定义
     */
    @Transactional(readOnly = true)
    public List<WfProcess> listProcesses(String businessType) {
        if (businessType != null && !businessType.isBlank()) {
            return processRepository.findByBusinessType(businessType);
        }
        return processRepository.findAll();
    }

    // ========================
    // 流程实例管理
    // ========================

    /**
     * 启动流程实例
     */
    public WfInstance startInstance(String processKey, String businessType,
                                    String businessId, Map<String, Object> variables) {
        return engine.startProcess(processKey, businessType, businessId, variables);
    }

    /**
     * 完成人工任务
     */
    public void completeTask(Long instanceId, Long nodeId, String operator,
                             String comment, Map<String, Object> newVariables) {
        engine.completeTask(instanceId, nodeId, operator, comment, newVariables);
    }

    /**
     * 取消流程实例
     */
    public void cancelInstance(Long instanceId) {
        engine.cancelProcess(instanceId);
    }

    /**
     * 查询实例状态
     */
    public WorkflowStatusDTO getInstanceStatus(Long instanceId) {
        return engine.getStatus(instanceId);
    }

    /**
     * 查询业务实体的流程状态
     */
    public WorkflowStatusDTO getBusinessStatus(String businessType, String businessId) {
        return engine.getBusinessStatus(businessType, businessId);
    }

    /**
     * 列出业务实体的所有流程实例
     */
    @Transactional(readOnly = true)
    public List<WfInstance> listBusinessInstances(String businessType, String businessId) {
        return instanceRepository.findByBusinessTypeAndBusinessId(businessType, businessId);
    }

    // ========================
    // 校验逻辑
    // ========================

    /**
     * 校验流程定义的完整性
     */
    private void validateProcessDefinition(Long processId) {
        List<WfNode> nodes = nodeRepository.findByProcessId(processId);
        List<WfTransition> transitions = transitionRepository.findByProcessId(processId);

        // 1. 必须有且只有 1 个 START 节点
        long startCount = nodes.stream()
                .filter(n -> NodeType.START.name().equals(n.getNodeType()))
                .count();
        if (startCount != 1) {
            throw new WorkflowException("流程必须有且只有 1 个 START 节点，当前: " + startCount);
        }

        // 2. 至少有 1 个 END 节点
        long endCount = nodes.stream()
                .filter(n -> NodeType.END.name().equals(n.getNodeType()))
                .count();
        if (endCount < 1) {
            throw new WorkflowException("流程至少需要 1 个 END 节点");
        }

        // 3. GATEWAY 节点至少有 2 条出口连线
        Set<Long> gatewayIds = nodes.stream()
                .filter(n -> NodeType.GATEWAY.name().equals(n.getNodeType()))
                .map(WfNode::getId)
                .collect(Collectors.toSet());

        for (Long gatewayId : gatewayIds) {
            long outgoing = transitions.stream()
                    .filter(t -> t.getSourceNodeId().equals(gatewayId))
                    .count();
            if (outgoing < 2) {
                throw new WorkflowException("GATEWAY 节点至少需要 2 条出口连线，节点ID: " + gatewayId);
            }
        }

        // 4. 连线引用的节点必须属于同一流程
        for (WfTransition trans : transitions) {
            boolean sourceValid = nodes.stream().anyMatch(n -> n.getId().equals(trans.getSourceNodeId()));
            boolean targetValid = nodes.stream().anyMatch(n -> n.getId().equals(trans.getTargetNodeId()));
            if (!sourceValid) {
                throw new WorkflowException("连线的起始节点不属于本流程: " + trans.getTransKey());
            }
            if (!targetValid) {
                throw new WorkflowException("连线的目标节点不属于本流程: " + trans.getTransKey());
            }
        }

        log.info("[流程服务] 流程校验通过: nodes={}, transitions={}", nodes.size(), transitions.size());
    }
}
