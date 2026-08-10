package com.comicdrama.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.comicdrama.task.entity.TaskQueue;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TaskQueueMapper extends BaseMapper<TaskQueue> {
}
