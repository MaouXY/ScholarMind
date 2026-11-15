package com.maou.apptemplateapi.module.literature.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@TableName("literature")
public class Literature {

    private Long id;
    private String title;
    private String author;
    private String source;
    /** 文献类别：期刊论文/会议论文/学位论文/图书/预印本/其他 */
    private String literatureType;
    /** 发表时间（精确到日）。publish_year 列保留为历史存档，应用层不再读写。 */
    private LocalDate publishDate;
    /** 在线公开时间（精确到日） */
    private LocalDate onlinePublishDate;
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
    private String parseStatus;
    private String parseError;
    private String summaryStatus;
    private LocalDateTime summaryUpdatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
