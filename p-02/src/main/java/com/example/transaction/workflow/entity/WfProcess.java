package com.example.transaction.workflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程定义 — 模板/蓝图
 *
 * 一个流程定义包含多个节点（WfNode）和多条连线（WfTransition）。
 * 流程定义的状态流转：草稿(0) → 已发布(1) → 已停用(2)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "t_wf_process")
public class WfProcess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 流程唯一标识，如 "order_approval" */
    @Column(name = "process_key", nullable = false, unique = true, length = 64)
    private String processKey;

    /** 流程名称，如 "订单审批流程" */
    @Column(name = "process_name", nullable = false, length = 128)
    private String processName;

    /** 绑定的业务类型，如 "order", "leave" */
    @Column(name = "business_type", nullable = false, length = 64)
    private String businessType;

    /** 流程描述 */
    @Column(length = 512)
    private String description;

    /** 版本号，每次修改 +1 */
    @Column(nullable = false)
    private Integer version;

    /** 状态：0=草稿, 1=已发布, 2=已停用 */
    @Column(nullable = false)
    private Integer status;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    // ===== 非持久化字段：从 t_wf_node / t_wf_transition 加载 =====

    @Transient
    private List<WfNode> nodes;

    @Transient
    private List<WfTransition> transitions;

    @PrePersist
    public void prePersist() {
        this.createTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
        if (this.version == null) this.version = 1;
        if (this.status == null) this.status = 0;
    }

    @PreUpdate
    public void preUpdate() {
        this.updateTime = LocalDateTime.now();
    }
}
