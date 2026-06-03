package com.maou.apptemplateapi.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 32) String username,
        @NotBlank @Size(min = 6, max = 64) String password,
        @NotBlank @Size(max = 32) String nickname,
        @Pattern(regexp = "STUDENT|TEACHER", message = "role must be STUDENT or TEACHER") String role,
        @Size(max = 128) String email
) {
}
