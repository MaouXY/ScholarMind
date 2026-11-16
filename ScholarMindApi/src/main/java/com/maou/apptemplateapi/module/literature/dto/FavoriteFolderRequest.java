package com.maou.apptemplateapi.module.literature.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新建 / 重命名收藏夹。
 */
public record FavoriteFolderRequest(
        @NotBlank @Size(max = 64) String name,
        @Size(max = 255) String description,
        Integer sort
) {
}
