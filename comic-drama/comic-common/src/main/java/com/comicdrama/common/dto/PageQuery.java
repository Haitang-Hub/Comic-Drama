package com.comicdrama.common.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 分页查询基类
 */
@Data
public class PageQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Min(1)
    private Integer page = 1;

    @Min(1)
    @Max(500)
    private Integer size = 10;

    /** 排序字段（驼峰自动转下划线由业务处理） */
    private String orderBy;

    /** asc/desc */
    private String orderDirection = "desc";

    public long offset() {
        return (long) (page - 1) * size;
    }
}
