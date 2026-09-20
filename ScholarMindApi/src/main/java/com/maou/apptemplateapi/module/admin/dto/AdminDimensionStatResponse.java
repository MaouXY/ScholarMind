package com.maou.apptemplateapi.module.admin.dto;

/**
 * 维度统计项。计数使用 Integer：全局 Jackson 会把 Long 序列化为字符串，前端按数字使用。
 */
public record AdminDimensionStatResponse(
        String name,
        Integer count
) {
}
