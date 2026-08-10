package com.comicdrama.common.result;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页结果
 */
@Data
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 数据列表 */
    private List<T> records = Collections.emptyList();

    /** 总记录数 */
    private long total;

    /** 当前页 */
    private long page;

    /** 每页大小 */
    private long size;

    public PageResult() {
    }

    public PageResult(List<T> records, long total, long page, long size) {
        this.records = records;
        this.total = total;
        this.page = page;
        this.size = size;
    }
}
