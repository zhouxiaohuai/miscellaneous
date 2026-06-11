package com.example.transaction.security.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 操作日志表（审计用）
 */
@Data
@Entity
@Table(name = "sys_oper_log")
public class SysOperLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50)
    private String module;

    @Column(length = 200)
    private String description;

    @Column(length = 500)
    private String requestUrl;

    @Column(length = 10)
    private String requestMethod;

    @Lob
    private String requestParams;

    @Lob
    private String responseResult;

    /** 0=失败 1=成功 */
    private Integer status = 1;

    @Column(length = 2000)
    private String errorMsg;

    private Long operUserId;

    @Column(length = 50)
    private String operUsername;

    @Column(length = 50)
    private String operIp;

    private LocalDateTime operTime;

    private Long costTime;

    @PrePersist
    public void prePersist() {
        this.operTime = LocalDateTime.now();
    }
}
