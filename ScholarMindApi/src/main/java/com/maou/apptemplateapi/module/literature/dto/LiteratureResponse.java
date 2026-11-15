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
        /** 文献类别：期刊论文/会议论文/学位论文/图书/预印本/其他 */
        String literatureType,
        /** 学科（literature_category 字典） */
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
        String keywords,
        Integer readCount,
        Integer downloadCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
