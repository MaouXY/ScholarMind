package com.maou.apptemplateapi.module.literature.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 管理员上传文献的题录字段。
 * <p>原「可见性」「状态」「审核」已移除：文献由管理员上传即发布，学生端只读。
 * 上传者（uploader_id）由后端取当前登录管理员，前端不再传。</p>
 * <p>{@code categoryId} 表示学科（复用 literature_category 字典），
 * {@code literatureType} 表示文献类别枚举。</p>
 */
public record LiteratureRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 255) String author,
        @Size(max = 255) String source,
        @Pattern(regexp = "期刊论文|会议论文|学位论文|图书|预印本|其他",
                message = "literatureType must be 期刊论文/会议论文/学位论文/图书/预印本/其他") String literatureType,
        Long categoryId,
        LocalDate publishDate,
        LocalDate onlinePublishDate,
        @Size(max = 32) String language,
        Long fileId,
        String abstractText,
        String keywords
) {
}
