package com.comicdrama.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.system.entity.OperationLog;

/**
 * 操作日志服务（Phase-1 仅只读分页，写入由 Phase-2 AOP 切面接入）
 */
public interface OperationLogService extends IService<OperationLog> {

    PageResult<OperationLog> page(PageQuery query, String module, String username);
}
