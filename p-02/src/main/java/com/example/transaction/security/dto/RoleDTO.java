package com.example.transaction.security.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 角色信息 DTO
 */
@Data
@Builder
public class RoleDTO {

    private Long id;
    private String roleName;
    private String roleKey;
    private Integer dataScope;
    private Integer status;
    private String remark;
    private List<Long> permissionIds;
}
