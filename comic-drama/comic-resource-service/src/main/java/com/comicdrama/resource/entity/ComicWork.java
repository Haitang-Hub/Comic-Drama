package com.comicdrama.resource.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.comicdrama.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 作品表（任务最终成片归档） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("comic_work")
public class ComicWork extends BaseEntity {

    private String workNo;
    private Long taskId;
    private Long userId;
    private String title;
    private String description;
    private String coverUrl;
    private String videoUrl;
    @TableField(exist = false)
    private String zipUrl;
    @TableField(exist = false)
    private Integer segmentCount;
    @TableField(exist = false)
    private String mergedFrom;
    private Integer duration;
    private String resolution;
    @TableField(exist = false)
    private String aspectRatio;
    @TableField(exist = false)
    private Long fileSize;
    private Integer status;
    private Integer isPublic;
    private Integer viewCount;
    private Integer likeCount;
    @TableField(exist = false)
    private String shareToken;
    @TableField(exist = false)
    private LocalDateTime shareExpire;
    @TableField(exist = false)
    private LocalDateTime publishTime;
}
