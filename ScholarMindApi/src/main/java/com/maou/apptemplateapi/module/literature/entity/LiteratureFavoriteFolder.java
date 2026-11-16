package com.maou.apptemplateapi.module.literature.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 收藏夹：用户自定义的文献分组。
 * <p>单归属模型——一篇文献只属于一个收藏夹（{@code literature_favorite.folder_id}），
 * {@code folder_id} 为空表示「未分类」。</p>
 */
@Getter
@Setter
@TableName("literature_favorite_folder")
public class LiteratureFavoriteFolder {

    private Long id;
    private Long userId;
    private String name;
    private String description;
    /** 展示顺序，越小越靠前 */
    private Integer sort;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
