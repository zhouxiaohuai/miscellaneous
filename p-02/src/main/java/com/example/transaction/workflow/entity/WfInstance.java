package com.example.transaction.workflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 流程实例 — 流程定义的一次运行
 *
 * 每个实例绑定一个业务实体（如订单#12345）。
 * 状态流转：运行中(0) → 已完成(1) / 已取消(2)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "t_wf_instance")
public class WfInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 流程定义 ID */
    @Column(name = "process_id", nullable = false)
    private Long processId;

    /** 业务类型，如 "order" */
    @Column(name = "business_type", nullable = false, length = 64)
    private String businessType;

    /** 业务主键，如 "12345" */
    @Column(name = "business_id", nullable = false, length = 64)
    private String businessId;

    /** 状态：0=运行中, 1=已完成, 2=已取消 */
    @Column(nullable = false)
    private Integer status;

    /** 流程变量 JSON，如 {"amount":1500, "vip":true} */
    @Column(columnDefinition = "JSON")
    private String variables;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    public void prePersist() {
        this.createTime = LocalDateTime.now();
        if (this.startTime == null) this.startTime = LocalDateTime.now();
        if (this.status == null) this.status = 0;
    }
}
