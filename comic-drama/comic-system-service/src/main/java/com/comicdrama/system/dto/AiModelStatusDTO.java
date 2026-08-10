package com.comicdrama.system.dto;

import lombok.Data;

/**
 * AI模型状态切换请求
 */
@Data
public class AiModelStatusDTO {

    /** 目标状态：0禁用 1启用 */
    private Integer status;
}
