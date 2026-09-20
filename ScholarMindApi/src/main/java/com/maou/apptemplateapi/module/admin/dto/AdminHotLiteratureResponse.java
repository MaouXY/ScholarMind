package com.maou.apptemplateapi.module.admin.dto;

/**
 * 热门文献统计项。计数使用 Integer：全局 Jackson 会把 Long 序列化为字符串，前端按数字使用；
 * literatureId 是主键 ID，必须保持 Long 以走全局的 ID 转字符串保护。
 */
public record AdminHotLiteratureResponse(
        Long literatureId,
        String title,
        String author,
        String categoryName,
        String status,
        String visibility,
        Integer readCount,
        Integer downloadCount,
        Integer favoriteCount,
        Integer noteCount
) {
}
