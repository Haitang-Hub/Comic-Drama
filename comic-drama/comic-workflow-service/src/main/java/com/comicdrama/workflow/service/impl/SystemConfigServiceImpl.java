package com.comicdrama.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.exception.BizException;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.common.result.ResultCode;
import com.comicdrama.workflow.entity.SystemConfig;
import com.comicdrama.workflow.mapper.SystemConfigMapper;
import com.comicdrama.workflow.service.SystemConfigService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class SystemConfigServiceImpl extends ServiceImpl<SystemConfigMapper, SystemConfig> implements SystemConfigService {

    @Override
    public PageResult<SystemConfig> page(PageQuery query, String keyword) {
        LambdaQueryWrapper<SystemConfig> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(SystemConfig::getConfigKey, keyword)
                    .or().like(SystemConfig::getConfigName, keyword);
        }
        wrapper.orderByAsc(SystemConfig::getConfigKey);
        Page<SystemConfig> page = new Page<>(query.getPage(), query.getSize());
        Page<SystemConfig> result = this.page(page, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public List<SystemConfig> listEnabled() {
        return this.list(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getStatus, 1)
                .orderByAsc(SystemConfig::getConfigKey));
    }

    @Override
    public String getValue(String configKey) {
        SystemConfig config = this.getOne(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, configKey)
                .eq(SystemConfig::getStatus, 1));
        return config == null ? null : config.getConfigValue();
    }

    @Override
    public void delete(Long id) {
        SystemConfig config = this.getById(id);
        if (config == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND);
        }
        if (config.getIsSystem() != null && config.getIsSystem() == 1) {
            throw new BizException("系统内置配置项不可删除");
        }
        this.removeById(id);
    }
}
