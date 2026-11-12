package com.maou.apptemplateapi.common.enums;

import java.util.Arrays;

/**
 * 平台角色。
 *
 * <p>产品只保留「用户端（STUDENT）」与「管理端（ADMIN）」两种角色。模板自带的
 * WORKER / OPERATOR / REVIEWER 已下线；TEACHER 仅作为历史数据兼容值保留——
 * 教师端已下线，存量教师账号不能登录（见 UserService#login），但历史 JWT 里的
 * roleCode=TEACHER 与存量 user_account.role 仍需能解析，避免直接抛异常。</p>
 */
public enum UserRole {
    STUDENT,
    /**
     * 历史角色：教师端已下线，登录一律拒绝，仅用于解析存量数据与旧令牌。
     */
    @Deprecated
    TEACHER,
    ADMIN;

    public static UserRole fromCode(String code) {
        return Arrays.stream(values())
                .filter(role -> role.name().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown role code: " + code));
    }
}

