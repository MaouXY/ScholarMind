package com.maou.apptemplateapi.module.user.dto;

import jakarta.validation.constraints.NotNull;

public record UserStatusRequest(
        @NotNull Integer status
) {
}
