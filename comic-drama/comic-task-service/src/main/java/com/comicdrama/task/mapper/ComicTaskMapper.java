package com.comicdrama.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.comicdrama.task.entity.ComicTask;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ComicTaskMapper extends BaseMapper<ComicTask> {
}
