package com.comicdrama.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.system.dto.AiModelTestResultDTO;
import com.comicdrama.system.entity.AiModelConfig;

import java.util.List;

/**
 * AI模型配置服务
 */
public interface AiModelConfigService extends IService<AiModelConfig> {

    /** 分页（密钥脱敏） */
    PageResult<AiModelConfig> page(PageQuery query, String keyword, Integer modelType);

    /** 脱敏后的详情 */
    AiModelConfig getMaskedById(Long id);

    /** 列表（密钥脱敏） */
    List<AiModelConfig> listEnabled();

    /** 启用/禁用 */
    void toggleStatus(Long id, Integer status);

    /**
     * 连通性测试：按模型配置的协议发起最小化探测请求，验证 API 地址可达性与密钥有效性。
     *
     * @param id 模型配置 ID
     * @return 测试结果（含耗时、状态码、说明）
     */
    AiModelTestResultDTO testConnection(Long id);
}
