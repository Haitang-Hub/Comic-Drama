package com.comicdrama.resource.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.resource.entity.ResourceFile;
import org.springframework.web.multipart.MultipartFile;

public interface ResourceFileService extends IService<ResourceFile> {

    ResourceFile upload(MultipartFile file, Long taskId, String sourceType) throws Exception;

    PageResult<ResourceFile> page(PageQuery query, Long taskId, Integer fileType);

    ResourceFile getById(Long id);

    void delete(Long id) throws Exception;

    String signUrl(Long id, int expireSeconds) throws Exception;
}
