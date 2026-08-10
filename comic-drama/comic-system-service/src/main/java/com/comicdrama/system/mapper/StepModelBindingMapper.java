package com.comicdrama.system.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/**
 * 步骤-模型绑定表 Mapper（供 system-service 清理绑定引用使用）
 */
@Mapper
public interface StepModelBindingMapper {

    /**
     * 清空指定模型的绑定引用（仅置空 model_config_id，model_type 为 NOT NULL 保留步骤类型）。
     */
    @Update("UPDATE step_model_binding SET model_config_id = NULL WHERE model_config_id = #{modelConfigId}")
    int clearBindingByModelConfigId(Long modelConfigId);
}
