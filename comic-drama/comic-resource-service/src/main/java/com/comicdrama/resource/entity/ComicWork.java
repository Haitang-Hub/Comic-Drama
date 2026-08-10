package com.comicdrama.resource.entity;

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
    private Integer segmentCount;
    private String mergedFrom;
    private Integer duration;
    private String resolution;
    private String aspectRatio;
    private Long fileSize;
    private Integer status;
    private Integer isPublic;
    private Integer viewCount;
    private Integer likeCount;
    private LocalDateTime publishTime;
}
