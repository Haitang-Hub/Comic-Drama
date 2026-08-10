package com.comicdrama.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.comicdrama.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 提示词模板表（全链路5阶段模板：大纲/分镜/素材解析/图像优化/视频连贯）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("prompt_template")
public class PromptTemplate extends BaseEntity {

    /** 模板编码（唯一标识） */
    private String templateCode;

    private String templateName;

    /** 所属阶段：1大纲 2分镜 3素材解析 4图像优化 5视频连贯 */
    private Integer stage;

    /** 模板内容（含变量占位符） */
    private String content;

    /** 变量列表（JSON数组，如 ["story","duration"]） */
    private String variables;

    private String description;

    /** 当前生效版本号 */
    private Integer currentVersion;

    /** 是否启用：0禁用 1启用（热生效，无需重启） */
    private Integer isEnabled;

    private Long createBy;
}
