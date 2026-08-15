package com.comicdrama.resource.dto;

import lombok.Data;

/**
 * 作品创建请求 DTO。
 */
@Data
public class WorkCreateDTO {

    private Long taskId;

    private String title;

    private String coverUrl;

    private String finalVideoUrl;

    private String primaryVideoUrl;

    private String resolution;

    private Integer duration;

    private Long userId;
}
