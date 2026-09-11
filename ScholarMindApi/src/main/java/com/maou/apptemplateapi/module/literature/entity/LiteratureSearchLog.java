package com.maou.apptemplateapi.module.literature.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 检索行为日志：记录语义检索与自然语言检索的查询、解析关键词和命中数量，
 * 供管理端第五阶段的热门关键词与检索热度统计使用。
 */
@Getter
@Setter
@TableName("literature_search_log")
public class LiteratureSearchLog {

    private Long id;
    private Long userId;
    private Long organizationId;
    private String queryText;
    private String keywords;
    private String searchType;
    private Long categoryId;
    private Integer resultCount;
    private Integer referenceCount;
    private Integer intentParsed;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
