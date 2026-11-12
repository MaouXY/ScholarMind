package com.maou.apptemplateapi.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 32) String username,
        @NotBlank @Size(min = 6, max = 64) String password,
        @NotBlank @Size(max = 32) String nickname,
        // 注册只开放用户端：教师端已下线，管理端账号由数据库/运维创建
        @Pattern(regexp = "STUDENT", message = "must be STUDENT") String role,
        @Size(max = 128) String email
) {
}
