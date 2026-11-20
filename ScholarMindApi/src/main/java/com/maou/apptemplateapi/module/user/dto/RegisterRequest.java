package com.maou.apptemplateapi.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 32) String username,
        @NotBlank @Size(min = 6, max = 64) String password,
        // 可选的重复密码：前端注册表单会提交两份密码做一致性校验；
        // 未传时由后端按单密码流程处理，保持旧接口调用方兼容。
        @Size(max = 64) String confirmPassword,
        // 昵称可选：不传时以用户名作为默认昵称，简化注册必填项
        @Size(max = 32) String nickname,
        // 注册只开放用户端：教师端已下线，管理端账号由数据库/运维创建
        @Pattern(regexp = "STUDENT", message = "must be STUDENT") String role,
        @Size(max = 128) String email
) {
}
