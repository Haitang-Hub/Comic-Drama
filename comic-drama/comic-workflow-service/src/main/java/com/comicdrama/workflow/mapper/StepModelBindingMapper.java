package com.comicdrama.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.comicdrama.workflow.entity.StepModelBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    /**
     * 清空指定模型的绑定引用（仅置空 model_config_id，model_type 为 NOT NULL 保留步骤类型）。
     * 用于删除/禁用 AI 模型配置时解除步骤绑定引用。
     */
    @Update("UPDATE step_model_binding SET model_config_id = NULL WHERE model_config_id = #{modelConfigId}")
    int clearBindingByModelConfigId(Long modelConfigId);
}
