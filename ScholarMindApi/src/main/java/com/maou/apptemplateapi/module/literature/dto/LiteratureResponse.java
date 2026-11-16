package com.maou.apptemplateapi.module.literature.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 文献响应。已移除「可见性 / 状态 / 审核人 / 审核时间 / 退回原因」：
 * 文献由管理员上传即发布，学生端只读，界面不再需要这些字段。
 */
public record LiteratureResponse(
        Long id,
        String title,
        String author,
        String source,
        /** 文献类别：学术期刊/学位论文（硕士）/学位论文（博士）/报纸 */
        String literatureType,
        /** 学科（literature_category 字典，中图法一级门类） */
        Long categoryId,
        String categoryName,
        LocalDate publishDate,
        LocalDate onlinePublishDate,
        String language,
        Long uploaderId,
        Long fileId,
        String fileUrl,
        String fileType,
        Long fileSize,
        String abstractText,
        String aiSummary,
        /** 参考文献列表原文；检索命中时可展示，列表页不展示 */
        String referencesText,
        String keywords,
        Integer readCount,
        Integer downloadCount,
        /** 被引次数（演示数据，管理员手填） */
        Integer citationCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
