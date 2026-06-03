package com.maou.apptemplateapi.module.user.dto;

import jakarta.validation.constraints.Size;

public record UserProfileUpdateRequest(
        @Size(max = 32) String nickname,
        @Size(max = 128) String email,
        @Size(max = 512) String avatar
) {
}
