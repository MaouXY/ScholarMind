package com.maou.apptemplateapi.module.literature.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 管理员上传文献的题录字段。
 * <p>原「可见性」「状态」「审核」已移除：文献由管理员上传即发布，学生端只读。
 * 上传者（uploader_id）由后端取当前登录管理员，前端不再传。</p>
 * <p>{@code categoryId} 表示学科（中图法一级门类，复用 literature_category 字典），
 * {@code literatureType} 表示文献类别枚举。</p>
 */
public record LiteratureRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 255) String author,
        @Size(max = 255) String source,
        @Pattern(regexp = "学术期刊|学位论文（硕士）|学位论文（博士）|报纸",
                message = "literatureType must be 学术期刊/学位论文（硕士）/学位论文（博士）/报纸") String literatureType,
        Long categoryId,
        LocalDate publishDate,
        LocalDate onlinePublishDate,
        @Size(max = 32) String language,
        Long fileId,
        String abstractText,
        String keywords,
        /** 参考文献列表原文，「参考文献」检索维度匹配用 */
        String referencesText,
        /** 被引次数（演示数据，管理员手填） */
        Integer citationCount
) {
}
