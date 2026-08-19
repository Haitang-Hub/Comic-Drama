package com.comicdrama.resource.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.resource.entity.ResourceFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface ResourceFileService extends IService<ResourceFile> {

    ResourceFile upload(MultipartFile file, Long taskId, String sourceType) throws Exception;

    PageResult<ResourceFile> page(PageQuery query, Long taskId, Integer fileType);

    ResourceFile getById(Long id);

    void delete(Long id) throws Exception;

    String signUrl(Long id, int expireSeconds) throws Exception;

    /**
     * 从各中间产物表（asset_image / storyboard_image / storyboard_audio / scene_video
     * / comic_work_timeline / comic_work）扫描生成出来的 URL，
     * 回填到 resource_file 表（按 objectKey 去重），解决「资源中心没数据」问题。
     *
     * @return summary: inserted, scanned, skipped
     */
    Map<String, Integer> syncFromArtifactTables();
}
