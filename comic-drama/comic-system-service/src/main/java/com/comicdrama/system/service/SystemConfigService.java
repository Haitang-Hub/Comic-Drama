package com.comicdrama.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.system.entity.SystemConfig;

import java.util.List;

/**
 * 系统配置服务
 */
public interface SystemConfigService extends IService<SystemConfig> {

    PageResult<SystemConfig> page(PageQuery query, String keyword);

    List<SystemConfig> listEnabled();

    /** 按 key 取配置值 */
    String getValue(String configKey);

    void delete(Long id);
}
