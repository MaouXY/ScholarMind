package com.maou.apptemplateapi.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordUpdateRequest(
        @NotBlank String oldPassword,
        @NotBlank @Size(min = 6, max = 64) String newPassword
) {
}
