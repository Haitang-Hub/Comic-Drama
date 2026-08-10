package com.comicdrama.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.comicdrama.workflow.entity.AiModelConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AiModelConfigMapper extends BaseMapper<AiModelConfig> {

    /**
     * 查询所有模型配置（含逻辑删除记录），供绑定页面显示历史绑定。
     * 绕过 MyBatis-Plus 逻辑删除过滤器，使用原生 SQL。
     */
    @Select("SELECT * FROM ai_model_config ORDER BY model_type, model_provider, status DESC")
    List<AiModelConfig> selectAllIncludeDeleted();
}
