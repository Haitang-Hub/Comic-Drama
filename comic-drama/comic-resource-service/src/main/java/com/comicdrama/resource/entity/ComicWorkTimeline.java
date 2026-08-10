package com.comicdrama.resource.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.comicdrama.common.entity.BaseCreateTimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("comic_work_timeline")
public class ComicWorkTimeline extends BaseCreateTimeEntity {

    private Long workId;

    private Long sceneGroupId;

    private Long storyboardId;

    private String videoUrl;

    private Integer orderIndex;

    private Integer duration;
}
