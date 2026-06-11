package com.example.transaction.security.enums;

import lombok.Getter;

/**
 * 权限类型枚举
 *
 * 统一管理菜单/按钮/接口三种权限类型
 */
@Getter
public enum PermissionType {

    DIRECTORY(1, "目录"),
    MENU(2, "菜单"),
    BUTTON(3, "按钮"),
    API(4, "接口");

    private final int code;
    private final String description;

    PermissionType(int code, String description) {
        this.code = code;
        this.description = description;
    }
}
