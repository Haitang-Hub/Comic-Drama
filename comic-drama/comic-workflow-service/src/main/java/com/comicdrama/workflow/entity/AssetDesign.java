package com.comicdrama.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.comicdrama.common.entity.BaseTimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 资产设计表（步骤3产物：人物/场景/道具/音色等资产设计） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("asset_design")
public class AssetDesign extends BaseTimeEntity {

    private Long taskId;
    private String assetType;
    /** 资产名称（含版本标识，如 小红_换装） */
    private String assetName;
    /** 基础资产名（无版本标识，用于归组） */
    private String baseAssetName;
    /** 衍生自（上一版本资产名，无则写"无"） */
    private String derivedFrom;
    /** 资产描述（详细描述） */
    private String assetDesc;
    /** 版本（由于变化产生的不同版本，从1开始） */
    private Integer version;
    private String resourceUrl;
}
