package com.example.transaction.workflow.engine;

import com.example.transaction.workflow.entity.*;
import com.example.transaction.workflow.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 流程引擎核心 — 驱动流程实例运行
 *
 * 核心逻辑：
 * 1. startProcess — 启动流程实例，自动推进到第一个人工任务或结束
 * 2. completeTask — 完成人工任务，继续推进
 * 3. advanceToNext — 递归推进：AUTO 自动执行、GATEWAY 条件分支、HUMAN 暂停等待、END 完成
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowEngine {

    private final WfProcessRepository processRepository;
    private final WfNodeRepository nodeRepository;
    private final WfTransitionRepository transitionRepository;
    private final WfInstanceRepository instanceRepository;
    private final WfInstanceNodeRepository instanceNodeRepository;
    private final ConditionEvaluator conditionEvaluator;
    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper;

    // ==========================================
    // 1. 启动流程实例
    // ==========================================

    /**
     * 启动一个新的流程实例
     *
     * @param processKey   流程定义标识，如 "order_approval"
     * @param businessType 业务类型，如 "order"
     * @param businessId   业务主键，如 "12345"
     * @param variables    流程变量，如 {"amount": 1500}
     * @return 创建的流程实例
     */
    @Transactional(rollbackFor = Exception.class)
    public WfInstance startProcess(String processKey, String businessType,
                                   String businessId, Map<String, Object> variables) {
        log.info("[流程引擎] 启动流程: processKey={}, businessType={}, businessId={}",
                processKey, businessType, businessId);

        // 1. 加载已发布的流程定义
        WfProcess process = processRepository.findByProcessKey(processKey)
                .orElseThrow(() -> new WorkflowException("流程定义不存在: " + processKey));
        if (process.getStatus() != 1) {
            throw new WorkflowException("流程未发布，无法启动: " + processKey);
        }

        // 2. 查找 START 节点
        WfNode startNode = nodeRepository.findByProcessIdAndNodeType(process.getId(), NodeType.START.name())
                .orElseThrow(() -> new WorkflowException("流程缺少 START 节点: " + processKey));

        // 3. 创建流程实例
        String variablesJson = toJson(variables);
        WfInstance instance = WfInstance.builder()
                .processId(process.getId())
                .businessType(businessType)
                .businessId(businessId)
                .status(InstanceStatus.RUNNING.getCode())
                .variables(variablesJson)
                .startTime(LocalDateTime.now())
                .build();
        instance = instanceRepository.save(instance);

        // 4. 标记 START 节点完成
        WfInstanceNode startInstanceNode = WfInstanceNode.builder()
                .instanceId(instance.getId())
                .nodeId(startNode.getId())
                .status(InstanceNodeStatus.COMPLETED.getCode())
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now())
                .build();
        instanceNodeRepository.save(startInstanceNode);

        // 5. 从 START 节点开始自动推进
        advanceToNext(instance, startNode, variables != null ? variables : new HashMap<>());

        log.info("[流程引擎] 流程启动成功: instanceId={}", instance.getId());
        return instance;
    }

    // ==========================================
    // 2. 完成人工任务
    // ==========================================

    /**
     * 完成一个人工任务节点，继续推进流程
     *
     * @param instanceId   流程实例 ID
     * @param nodeDefId    节点定义 ID（t_wf_node.id）
     * @param operator     处理人
     * @param comment      审批意见
     * @param newVariables 新增/更新的流程变量（会合并到现有变量中）
     */
    @Transactional(rollbackFor = Exception.class)
    public void completeTask(Long instanceId, Long nodeDefId, String operator,
                             String comment, Map<String, Object> newVariables) {
        log.info("[流程引擎] 完成任务: instanceId={}, nodeId={}, operator={}",
                instanceId, nodeDefId, operator);

        // 1. 校验实例状态
        WfInstance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new WorkflowException("流程实例不存在: " + instanceId));
        if (instance.getStatus() != InstanceStatus.RUNNING.getCode()) {
            throw new WorkflowException("流程实例不在运行中，无法完成任务: " + instanceId);
        }

        // 2. 校验节点状态
        WfInstanceNode instanceNode = instanceNodeRepository.findByInstanceIdAndNodeId(instanceId, nodeDefId)
                .orElseThrow(() -> new WorkflowException("实例节点不存在: instanceId=" + instanceId + ", nodeId=" + nodeDefId));
        if (instanceNode.getStatus() != InstanceNodeStatus.ACTIVE.getCode()) {
            throw new WorkflowException("节点不在进行中，无法完成: status=" + instanceNode.getStatus());
        }

        // 3. 标记节点完成
        instanceNode.setStatus(InstanceNodeStatus.COMPLETED.getCode());
        instanceNode.setOperator(operator);
        instanceNode.setComment(comment);
        instanceNode.setEndTime(LocalDateTime.now());
        instanceNodeRepository.save(instanceNode);

        // 4. 合并新变量到实例
        Map<String, Object> mergedVariables = mergeVariables(instance.getVariables(), newVariables);
        instance.setVariables(toJson(mergedVariables));
        instanceRepository.save(instance);

        // 5. 加载节点定义
        WfNode nodeDef = nodeRepository.findById(nodeDefId)
                .orElseThrow(() -> new WorkflowException("节点定义不存在: " + nodeDefId));

        // 6. 继续推进
        advanceToNext(instance, nodeDef, mergedVariables);

        log.info("[流程引擎] 任务完成: instanceId={}, nodeId={}", instanceId, nodeDefId);
    }

    // ==========================================
    // 3. 取消流程实例
    // ==========================================

    /**
     * 取消一个运行中的流程实例
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelProcess(Long instanceId) {
        log.info("[流程引擎] 取消流程: instanceId={}", instanceId);

        WfInstance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new WorkflowException("流程实例不存在: " + instanceId));
        if (instance.getStatus() != InstanceStatus.RUNNING.getCode()) {
            throw new WorkflowException("流程实例不在运行中，无法取消: " + instanceId);
        }

        // 标记实例取消
        instance.setStatus(InstanceStatus.CANCELLED.getCode());
        instance.setEndTime(LocalDateTime.now());
        instanceRepository.save(instance);

        // 标记所有待处理/进行中的节点为已跳过
        List<WfInstanceNode> activeNodes = instanceNodeRepository.findByInstanceIdAndStatus(
                instanceId, InstanceNodeStatus.ACTIVE.getCode());
        activeNodes.addAll(instanceNodeRepository.findByInstanceIdAndStatus(
                instanceId, InstanceNodeStatus.PENDING.getCode()));

        for (WfInstanceNode node : activeNodes) {
            node.setStatus(InstanceNodeStatus.SKIPPED.getCode());
            node.setEndTime(LocalDateTime.now());
            instanceNodeRepository.save(node);
        }

        log.info("[流程引擎] 流程已取消: instanceId={}, 跳过{}个节点", instanceId, activeNodes.size());
    }

    // ==========================================
    // 4. 查询状态
    // ==========================================

    /**
     * 查询流程实例的完整状态（当前节点 + 历史 + 流程定义）
     */
    public WorkflowStatusDTO getStatus(Long instanceId) {
        WfInstance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new WorkflowException("流程实例不存在: " + instanceId));

        WfProcess process = processRepository.findById(instance.getProcessId())
                .orElseThrow(() -> new WorkflowException("流程定义不存在: " + instance.getProcessId()));

        List<WfInstanceNode> allInstanceNodes = instanceNodeRepository.findByInstanceId(instanceId);

        List<WfInstanceNode> activeNodes = allInstanceNodes.stream()
                .filter(n -> n.getStatus() == InstanceNodeStatus.ACTIVE.getCode())
                .collect(Collectors.toList());

        List<WfInstanceNode> historyNodes = allInstanceNodes.stream()
                .filter(n -> n.getStatus() == InstanceNodeStatus.COMPLETED.getCode()
                        || n.getStatus() == InstanceNodeStatus.SKIPPED.getCode())
                .collect(Collectors.toList());

        List<WfNode> allNodes = nodeRepository.findByProcessId(process.getId());
        List<WfTransition> allTransitions = transitionRepository.findByProcessId(process.getId());

        return WorkflowStatusDTO.builder()
                .instance(instance)
                .process(process)
                .activeNodes(activeNodes)
                .historyNodes(historyNodes)
                .allNodes(allNodes)
                .allTransitions(allTransitions)
                .build();
    }

    /**
     * 查询业务实体的当前流程状态
     */
    public WorkflowStatusDTO getBusinessStatus(String businessType, String businessId) {
        Optional<WfInstance> runningInstance = instanceRepository
                .findByBusinessTypeAndBusinessIdAndStatus(businessType, businessId, InstanceStatus.RUNNING.getCode());

        if (runningInstance.isEmpty()) {
            // 返回最近一次的实例（已完成/已取消）
            List<WfInstance> instances = instanceRepository.findByBusinessTypeAndBusinessId(businessType, businessId);
            if (instances.isEmpty()) {
                return null;
            }
            // 按创建时间倒序，取最新的
            instances.sort((a, b) -> b.getCreateTime().compareTo(a.getCreateTime()));
            return getStatus(instances.get(0).getId());
        }

        return getStatus(runningInstance.get().getId());
    }

    // ==========================================
    // 内部：自动推进逻辑（核心中的核心）
    // ==========================================

    /**
     * 从当前节点推进到下一个节点
     *
     * 递归推进，直到遇到：
     * - HUMAN 任务 → 停下，等待 completeTask()
     * - END 节点 → 标记实例完成
     */
    private void advanceToNext(WfInstance instance, WfNode currentNode, Map<String, Object> variables) {
        log.debug("[流程引擎] 推进: 从节点 {} ({})", currentNode.getNodeKey(), currentNode.getNodeType());

        // 获取当前节点的所有出口连线
        List<WfTransition> transitions = transitionRepository
                .findBySourceNodeIdOrderBySortOrder(currentNode.getId());

        if (transitions.isEmpty()) {
            // 没有出口连线 — 如果不是 END 节点则报错
            if (!NodeType.END.name().equals(currentNode.getNodeType())) {
                throw new WorkflowException("节点没有出口连线: " + currentNode.getNodeKey());
            }
            return;
        }

        // 确定目标节点
        WfTransition matchedTransition;
        if (NodeType.GATEWAY.name().equals(currentNode.getNodeType())) {
            // 网关：按优先级评估条件，第一个匹配的胜出
            matchedTransition = transitions.stream()
                    .filter(t -> conditionEvaluator.evaluate(t.getConditionExpr(), variables))
                    .findFirst()
                    .orElseThrow(() -> new WorkflowException(
                            "网关节点没有匹配的分支: " + currentNode.getNodeKey() + "，变量: " + variables));
            log.info("[流程引擎] 网关 {} 选择分支: {} → {}",
                    currentNode.getNodeKey(), matchedTransition.getTransKey(),
                    matchedTransition.getConditionExpr());
        } else {
            // 非网关：取唯一的出口连线
            matchedTransition = transitions.get(0);
        }

        // 加载目标节点
        WfNode targetNode = nodeRepository.findById(matchedTransition.getTargetNodeId())
                .orElseThrow(() -> new WorkflowException("目标节点不存在: " + matchedTransition.getTargetNodeId()));

        log.info("[流程引擎] 推进: {} → {} ({})",
                currentNode.getNodeKey(), targetNode.getNodeKey(), targetNode.getNodeType());

        // 根据目标节点类型处理
        switch (NodeType.valueOf(targetNode.getNodeType())) {
            case END -> handleEndNode(instance, targetNode);
            case TASK -> handleTaskNode(instance, targetNode, variables);
            case GATEWAY -> handleGatewayNode(instance, targetNode, variables);
            case START -> throw new WorkflowException("流程不应推进到 START 节点");
        }
    }

    /**
     * 处理 END 节点 — 标记实例完成
     */
    private void handleEndNode(WfInstance instance, WfNode endNode) {
        log.info("[流程引擎] 到达 END 节点: {}，完成流程实例: {}",
                endNode.getNodeKey(), instance.getId());

        // 创建 END 节点的实例记录
        WfInstanceNode endInstanceNode = WfInstanceNode.builder()
                .instanceId(instance.getId())
                .nodeId(endNode.getId())
                .status(InstanceNodeStatus.COMPLETED.getCode())
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now())
                .build();
        instanceNodeRepository.save(endInstanceNode);

        // 标记实例完成
        instance.setStatus(InstanceStatus.COMPLETED.getCode());
        instance.setEndTime(LocalDateTime.now());
        instanceRepository.save(instance);
    }

    /**
     * 处理 TASK 节点
     * - HUMAN: 创建进行中的实例节点，停下等待
     * - AUTO: 执行处理器，标记完成，继续推进
     */
    private void handleTaskNode(WfInstance instance, WfNode taskNode, Map<String, Object> variables) {
        String taskType = taskNode.getTaskType();

        if (TaskType.HUMAN.name().equals(taskType)) {
            // 人工任务 — 创建 ACTIVE 实例节点，停下等待
            WfInstanceNode humanNode = WfInstanceNode.builder()
                    .instanceId(instance.getId())
                    .nodeId(taskNode.getId())
                    .status(InstanceNodeStatus.ACTIVE.getCode())
                    .startTime(LocalDateTime.now())
                    .build();
            instanceNodeRepository.save(humanNode);
            log.info("[流程引擎] 人工任务 {} 等待处理", taskNode.getNodeKey());

        } else if (TaskType.AUTO.name().equals(taskType)) {
            // 自动任务 — 执行处理器
            log.info("[流程引擎] 执行自动任务: {} (handlerBean={})", taskNode.getNodeKey(), taskNode.getHandlerBean());

            // 创建 ACTIVE 实例节点
            WfInstanceNode autoNode = WfInstanceNode.builder()
                    .instanceId(instance.getId())
                    .nodeId(taskNode.getId())
                    .status(InstanceNodeStatus.ACTIVE.getCode())
                    .startTime(LocalDateTime.now())
                    .build();
            autoNode = instanceNodeRepository.save(autoNode);

            // 执行自动任务处理器
            executeAutoHandler(taskNode, instance);

            // 标记完成
            autoNode.setStatus(InstanceNodeStatus.COMPLETED.getCode());
            autoNode.setEndTime(LocalDateTime.now());
            instanceNodeRepository.save(autoNode);

            // 继续推进
            advanceToNext(instance, taskNode, variables);

        } else {
            throw new WorkflowException("未知的任务类型: " + taskType + "，节点: " + taskNode.getNodeKey());
        }
    }

    /**
     * 处理 GATEWAY 节点 — 创建实例记录，继续推进
     */
    private void handleGatewayNode(WfInstance instance, WfNode gatewayNode, Map<String, Object> variables) {
        WfInstanceNode gatewayInstanceNode = WfInstanceNode.builder()
                .instanceId(instance.getId())
                .nodeId(gatewayNode.getId())
                .status(InstanceNodeStatus.COMPLETED.getCode())
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now())
                .build();
        instanceNodeRepository.save(gatewayInstanceNode);

        // 继续推进（条件评估在 advanceToNext 中处理）
        advanceToNext(instance, gatewayNode, variables);
    }

    /**
     * 执行自动任务处理器
     */
    private void executeAutoHandler(WfNode node, WfInstance instance) {
        String beanName = node.getHandlerBean();
        if (beanName == null || beanName.isBlank()) {
            log.warn("[流程引擎] 自动任务没有配置 handlerBean: {}", node.getNodeKey());
            return;
        }

        try {
            AutoTaskHandler handler = applicationContext.getBean(beanName, AutoTaskHandler.class);
            handler.execute(instance, node);
            log.info("[流程引擎] 自动任务执行成功: {} → {}", node.getNodeKey(), beanName);
        } catch (Exception e) {
            log.error("[流程引擎] 自动任务执行失败: {} → {}", node.getNodeKey(), beanName, e);
            throw new WorkflowException("自动任务执行失败: " + node.getNodeKey(), e);
        }
    }

    // ==========================================
    // 工具方法
    // ==========================================

    /**
     * 合并新变量到现有变量
     */
    private Map<String, Object> mergeVariables(String existingJson, Map<String, Object> newVariables) {
        Map<String, Object> merged = fromJson(existingJson);
        if (newVariables != null) {
            merged.putAll(newVariables);
        }
        return merged;
    }

    /**
     * JSON 字符串 → Map
     */
    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[流程引擎] JSON 解析失败: {}", json, e);
            return new HashMap<>();
        }
    }

    /**
     * Map → JSON 字符串
     */
    private String toJson(Map<String, Object> map) {
        if (map == null) return null;
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            log.warn("[流程引擎] JSON 序列化失败: {}", map, e);
            return "{}";
        }
    }
}
