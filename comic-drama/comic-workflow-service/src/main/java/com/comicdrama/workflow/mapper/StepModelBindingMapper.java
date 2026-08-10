package com.comicdrama.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.comicdrama.workflow.entity.StepModelBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 步骤-模型绑定 Mapper。
 */
@Mapper
public interface StepModelBindingMapper extends BaseMapper<StepModelBinding> {

    /**
     * 查询所有绑定关系，按 step_order 排序。
     */
    @Select("SELECT * FROM step_model_binding ORDER BY step_order")
    List<StepModelBinding> selectAllOrdered();

    /**
     * 根据步骤编码查询绑定。
     */
    @Select("SELECT * FROM step_model_binding WHERE step_code = #{stepCode}")
    StepModelBinding selectByStepCode(String stepCode);

    /**
     * 根据模型配置 ID 查询所有关联的绑定。
     */
    @Select("SELECT * FROM step_model_binding WHERE model_config_id = #{modelConfigId}")
    List<StepModelBinding> selectByModelConfigId(Long modelConfigId);
}
