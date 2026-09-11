package com.maou.apptemplateapi.module.literature.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("literature_chunk")
public class LiteratureChunk {

    private Long id;
    private Long literatureId;
    private Integer chunkIndex;
    private String chunkText;
    private Integer pageNo;
    private Integer tokenCount;
    private String embeddingProvider;
    private String vectorStoreStatus;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
