package com.comicdrama.resource.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 资源文件表（统一管理存储中的图片/音频/视频/素材）。
 * 注意：本表仅有 create_time + deleted，无 update_time，故不继承审计基类。
 */
@Data
@TableName("resource_file")
public class ResourceFile implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long taskId;

    private Long userId;

    private String fileName;

    private String originalName;

    /** 文件类型：1图片 2音频 3视频 4文档 5其他 */
    private Integer fileType;

    private String mimeType;

    private Long fileSize;

    private Integer width;

    private Integer height;

    private java.math.BigDecimal duration;

    private String bucketName;

    private String objectKey;

    private String fileUrl;

    private String tempUrl;

    private LocalDateTime tempUrlExpire;

    private String md5;

    private String sourceType;

    private String sourceNode;

    private Integer isPublic;

    private LocalDateTime expireTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
