package com.example.transaction.workflow.controller;

import com.example.transaction.workflow.engine.WorkflowException;
import com.example.transaction.workflow.engine.WorkflowStatusDTO;
import com.example.transaction.workflow.entity.*;
import com.example.transaction.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 流程引擎 REST 接口
 *
 * 三大功能：
 * 1. 流程定义管理（创建/发布/停用/查询）
 * 2. 流程实例管理（启动/完成任务/取消）
 * 3. 业务实体查询（查询某笔订单走到哪一步了）
 */
@Slf4j
@RestController
@RequestMapping("/api/wf")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    // ================================
    // 流程定义管理
    // ================================

    /**
     * 创建流程定义
     * POST /api/wf/process
     *
     * Body 示例见下方
     */
    @PostMapping("/process")
    public Map<String, Object> createProcess(@RequestBody Map<String, Object> body) {
        try {
            // 解析 process
            Map<String, Object> processMap = (Map<String, Object>) body.get("process");
            WfProcess process = WfProcess.builder()
                    .processKey((String) processMap.get("processKey"))
                    .processName((String) processMap.get("processName"))
                    .businessType((String) processMap.get("businessType"))
                    .description((String) processMap.get("description"))
                    .build();

            // 解析 nodes
            List<Map<String, Object>> nodeMaps = (List<Map<String, Object>>) body.get("nodes");
            List<WfNode> nodes = new ArrayList<>();
            for (Map<String, Object> nMap : nodeMaps) {
                WfNode node = WfNode.builder()
                        .nodeKey((String) nMap.get("nodeKey"))
                        .nodeName((String) nMap.get("nodeName"))
                        .nodeType((String) nMap.get("nodeType"))
                        .taskType((String) nMap.get("taskType"))
                        .handlerBean((String) nMap.get("handlerBean"))
                        .positionX(nMap.containsKey("positionX") ? (Integer) nMap.get("positionX") : 0)
                        .positionY(nMap.containsKey("positionY") ? (Integer) nMap.get("positionY") : 0)
                        .build();
                nodes.add(node);
            }

            // 解析 transitions（使用 nodeKey 引用）
            List<Map<String, Object>> transitionMaps = (List<Map<String, Object>>) body.get("transitions");

            WfProcess result = workflowService.createProcessFromKeys(process, nodes, transitionMaps);
            return result("流程定义创建成功", result);

        } catch (WorkflowException e) {
            return error(e.getMessage());
        } catch (Exception e) {
            log.error("[流程接口] 创建流程定义失败", e);
            return error("创建失败: " + e.getMessage());
        }
    }

    /**
     * 获取流程定义详情（含节点和连线）
     * GET /api/wf/process/{id}
     */
    @GetMapping("/process/{id}")
    public Map<String, Object> getProcess(@PathVariable Long id) {
        try {
            WfProcess process = workflowService.getProcessDetail(id);
            return result("查询成功", process);
        } catch (WorkflowException e) {
            return error(e.getMessage());
        }
    }

    /**
     * 列出流程定义
     * GET /api/wf/process?businessType=order
     */
    @GetMapping("/process")
    public Map<String, Object> listProcesses(
            @RequestParam(required = false) String businessType) {
        List<WfProcess> processes = workflowService.listProcesses(businessType);
        return result("查询成功", processes);
    }

    /**
     * 发布流程
     * PUT /api/wf/process/{id}/publish
     */
    @PutMapping("/process/{id}/publish")
    public Map<String, Object> publishProcess(@PathVariable Long id) {
        try {
            WfProcess process = workflowService.publishProcess(id);
            return result("流程已发布", process);
        } catch (WorkflowException e) {
            return error(e.getMessage());
        }
    }

    /**
     * 停用流程
     * PUT /api/wf/process/{id}/stop
     */
    @PutMapping("/process/{id}/stop")
    public Map<String, Object> stopProcess(@PathVariable Long id) {
        try {
            WfProcess process = workflowService.stopProcess(id);
            return result("流程已停用", process);
        } catch (WorkflowException e) {
            return error(e.getMessage());
        }
    }

    // ================================
    // 流程实例管理
    // ================================

    /**
     * 启动流程实例
     * POST /api/wf/instance/start
     */
    @PostMapping("/instance/start")
    public Map<String, Object> startInstance(@RequestBody Map<String, Object> body) {
        try {
            String processKey = (String) body.get("processKey");
            String businessType = (String) body.get("businessType");
            String businessId = (String) body.get("businessId");
            Map<String, Object> variables = (Map<String, Object>) body.get("variables");

            WfInstance instance = workflowService.startInstance(processKey, businessType, businessId, variables);
            return result("流程实例已启动", instance);

        } catch (WorkflowException e) {
            return error(e.getMessage());
        } catch (Exception e) {
            log.error("[流程接口] 启动流程实例失败", e);
            return error("启动失败: " + e.getMessage());
        }
    }

    /**
     * 完成人工任务
     * POST /api/wf/instance/{instanceId}/node/{nodeId}/complete
     */
    @PostMapping("/instance/{instanceId}/node/{nodeId}/complete")
    public Map<String, Object> completeTask(
            @PathVariable Long instanceId,
            @PathVariable Long nodeId,
            @RequestBody Map<String, Object> body) {
        try {
            String operator = (String) body.get("operator");
            String comment = (String) body.get("comment");
            Map<String, Object> variables = (Map<String, Object>) body.get("variables");

            workflowService.completeTask(instanceId, nodeId, operator, comment, variables);
            return result("任务已完成", null);

        } catch (WorkflowException e) {
            return error(e.getMessage());
        } catch (Exception e) {
            log.error("[流程接口] 完成任务失败", e);
            return error("完成任务失败: " + e.getMessage());
        }
    }

    /**
     * 取消流程实例
     * POST /api/wf/instance/{instanceId}/cancel
     */
    @PostMapping("/instance/{instanceId}/cancel")
    public Map<String, Object> cancelInstance(@PathVariable Long instanceId) {
        try {
            workflowService.cancelInstance(instanceId);
            return result("流程实例已取消", null);
        } catch (WorkflowException e) {
            return error(e.getMessage());
        }
    }

    /**
     * 查询实例状态
     * GET /api/wf/instance/{instanceId}
     */
    @GetMapping("/instance/{instanceId}")
    public Map<String, Object> getInstanceStatus(@PathVariable Long instanceId) {
        try {
            WorkflowStatusDTO status = workflowService.getInstanceStatus(instanceId);
            return result("查询成功", status);
        } catch (WorkflowException e) {
            return error(e.getMessage());
        }
    }

    // ================================
    // 业务实体查询
    // ================================

    /**
     * 查询业务实体的流程状态
     * GET /api/wf/business/status?businessType=order&businessId=12345
     */
    @GetMapping("/business/status")
    public Map<String, Object> getBusinessStatus(
            @RequestParam String businessType,
            @RequestParam String businessId) {
        try {
            WorkflowStatusDTO status = workflowService.getBusinessStatus(businessType, businessId);
            if (status == null) {
                return result("该业务实体没有关联的流程", null);
            }
            return result("查询成功", status);
        } catch (WorkflowException e) {
            return error(e.getMessage());
        }
    }

    /**
     * 列出业务实体的所有流程实例
     * GET /api/wf/business/instances?businessType=order&businessId=12345
     */
    @GetMapping("/business/instances")
    public Map<String, Object> listBusinessInstances(
            @RequestParam String businessType,
            @RequestParam String businessId) {
        List<WfInstance> instances = workflowService.listBusinessInstances(businessType, businessId);
        return result("查询成功", instances);
    }

    // ================================
    // API 总览
    // ================================

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        Map<String, Object> api = new LinkedHashMap<>();
        api.put("模块", "通用流程引擎");
        api.put("说明", "支持流程设计、条件分支、业务绑定、状态追踪");

        Map<String, String> endpoints = new LinkedHashMap<>();
        endpoints.put("POST /api/wf/process", "创建流程定义");
        endpoints.put("GET /api/wf/process/{id}", "获取流程详情");
        endpoints.put("GET /api/wf/process", "列出流程定义");
        endpoints.put("PUT /api/wf/process/{id}/publish", "发布流程");
        endpoints.put("PUT /api/wf/process/{id}/stop", "停用流程");
        endpoints.put("POST /api/wf/instance/start", "启动流程实例");
        endpoints.put("POST /api/wf/instance/{id}/node/{nodeId}/complete", "完成人工任务");
        endpoints.put("POST /api/wf/instance/{id}/cancel", "取消流程实例");
        endpoints.put("GET /api/wf/instance/{id}", "查询实例状态");
        endpoints.put("GET /api/wf/business/status", "查询业务实体流程状态");
        endpoints.put("GET /api/wf/business/instances", "列出业务实体的流程实例");
        api.put("接口列表", endpoints);

        Map<String, String> nodeTypes = new LinkedHashMap<>();
        nodeTypes.put("START", "开始节点（每流程 1 个，自动通过）");
        nodeTypes.put("END", "结束节点（1 个或多个，标记完成）");
        nodeTypes.put("TASK/HUMAN", "人工任务（暂停等待人处理）");
        nodeTypes.put("TASK/AUTO", "自动任务（Spring Bean 自动执行）");
        nodeTypes.put("GATEWAY", "网关节点（SpEL 条件分支）");
        api.put("节点类型", nodeTypes);

        return result("流程引擎 API 总览", api);
    }

    // ================================
    // 工具方法
    // ================================

    private Map<String, Object> result(String message, Object data) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("success", true);
        map.put("message", message);
        if (data != null) {
            map.put("data", data);
        }
        return map;
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("success", false);
        map.put("message", message);
        return map;
    }
}
