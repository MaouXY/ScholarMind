package com.maou.apptemplateapi.module.citation.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 引用记录：用户对某篇文献收藏的引用条目（含指定格式的引用文本）。
 */
@Getter
@Setter
@TableName("citation_record")
public class CitationRecord {

    private Long id;
    private Long userId;
    private Long literatureId;
    private String citationStyle;
    private String citationText;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
