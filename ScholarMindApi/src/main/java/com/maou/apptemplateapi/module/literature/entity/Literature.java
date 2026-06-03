package com.maou.apptemplateapi.module.literature.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("literature")
public class Literature {

    private Long id;
    private String title;
    private String author;
    private String source;
    private Integer publishYear;
    private String language;
    private Long categoryId;
    private Long uploaderId;
    private String visibility;
    private String status;
    private Long fileId;
    private String fileUrl;
    private String fileType;
    private Long fileSize;
    private String abstractText;
    private String aiSummary;
    private String keywords;
    private Integer readCount;
    private Integer downloadCount;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private String rejectReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
