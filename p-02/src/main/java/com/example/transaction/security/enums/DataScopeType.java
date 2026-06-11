package com.example.transaction.security.enums;

import lombok.Getter;

/**
 * 数据范围枚举
 *
 * 控制用户能看到的数据范围：
 * - ALL: 全部数据（超级管理员）
 * - DEPT: 本部门数据
 * - DEPT_AND_CHILDREN: 本部门及下级部门数据
 * - SELF: 仅本人数据
 */
@Getter
public enum DataScopeType {

    ALL(1, "全部数据"),
    DEPT(2, "本部门数据"),
    DEPT_AND_CHILDREN(3, "本部门及下级部门数据"),
    SELF(4, "仅本人数据");

    private final int code;
    private final String description;

    DataScopeType(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public static DataScopeType fromCode(int code) {
        for (DataScopeType type : values()) {
            if (type.code == code) return type;
        }
        return SELF;  // 默认最小权限
    }
}
