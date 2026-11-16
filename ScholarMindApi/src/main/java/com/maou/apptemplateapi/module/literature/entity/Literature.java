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
    /** 文献类别：学术期刊/学位论文（硕士）/学位论文（博士）/报纸 */
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
    /** 参考文献列表原文，供「参考文献」检索维度匹配 */
    private String referencesText;
    private String keywords;
    private Integer readCount;
    private Integer downloadCount;
    /** 被引次数：由管理员手填，演示数据 */
    private Integer citationCount;
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
