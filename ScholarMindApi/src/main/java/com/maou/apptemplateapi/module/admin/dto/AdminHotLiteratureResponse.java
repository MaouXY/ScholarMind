package com.maou.apptemplateapi.module.admin.dto;

/**
 * 热门文献统计项。计数使用 Integer：全局 Jackson 会把 Long 序列化为字符串，前端按数字使用；
 * literatureId 是主键 ID，必须保持 Long 以走全局的 ID 转字符串保护。
 * <p>文献上传即发布，不再有可见性与审核状态维度；改为展示「文献类别」便于运营区分资源结构。</p>
 */
public record AdminHotLiteratureResponse(
        Long literatureId,
        String title,
        String author,
        String categoryName,
        String literatureType,
        Integer readCount,
        Integer downloadCount,
        Integer favoriteCount,
        Integer noteCount
) {
}
