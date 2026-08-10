package com.comicdrama.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.comicdrama.common.entity.BaseCreateTimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 操作日志表（用户与管理员操作审计）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("operation_log")
public class OperationLog extends BaseCreateTimeEntity {

    private Long userId;

    private String username;

    /** 功能模块（task/prompt/resource等） */
    private String module;

    /** 业务类型（create/update/delete/regenerate等） */
    private String businessType;

    /** 请求方法（类.方法） */
    private String method;

    private String requestUrl;

    /** HTTP方法（GET/POST等） */
    private String requestMethod;

    private String requestParam;

    private String responseData;

    private String ip;

    private String location;

    /** 操作状态：0失败 1成功 */
    private Integer status;

    private String errorMsg;

    /** 耗时（毫秒） */
    private Integer costTime;
}
