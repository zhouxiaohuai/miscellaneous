package com.example.transaction.security.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户信息 DTO（脱敏，不返回密码）
 */
@Data
@Builder
public class UserDTO {

    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String phone;
    private String avatar;
    private Long deptId;
    private String deptName;
    private Integer status;
    private LocalDateTime loginTime;
    private LocalDateTime createdAt;
    private List<RoleDTO> roles;
}
