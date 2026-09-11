package com.maou.apptemplateapi.module.admin.dto;

/**
 * 热门关键词统计：同一关键词在文献标签、检索行为和问答提问中的热度。
 * 数值字段使用 Integer，避免全局 Long→String 序列化导致前端拿到字符串。
 */
public record AdminKeywordStatResponse(
        String keyword,
        Integer literatureCount,
        Integer searchCount,
        Integer qaCount,
        Integer total
) {
}
