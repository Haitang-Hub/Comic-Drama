package com.comicdrama.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.comicdrama.system.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
