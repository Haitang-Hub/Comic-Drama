package com.comicdrama.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.comicdrama.common.entity.BaseTimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 资产图片表（步骤4产物：资产绘图） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("asset_image")
public class AssetImage extends BaseTimeEntity {

    private Long taskId;
    private Long assetId;
    private String assetType;
    private String assetName;
    private String imageUrl;
    private String thumbnailUrl;
    private Long baseImageId;
    private String baseImageUrl;
    private String promptUsed;
    private String generateParams;
    private Integer status;
    private Integer width;
    private Integer height;
}
