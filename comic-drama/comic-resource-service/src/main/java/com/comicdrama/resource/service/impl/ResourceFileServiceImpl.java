package com.comicdrama.resource.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.exception.BizException;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.common.result.ResultCode;
import com.comicdrama.common.storage.StorageProperties;
import com.comicdrama.common.storage.StorageService;
import com.comicdrama.common.util.SecurityUtils;
import com.comicdrama.resource.entity.ResourceFile;
import com.comicdrama.resource.mapper.ResourceFileMapper;
import com.comicdrama.resource.service.ResourceFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceFileServiceImpl extends ServiceImpl<ResourceFileMapper, ResourceFile> implements ResourceFileService {

    private final StorageService storageService;
    private final StorageProperties storageProperties;

    @Override
    public ResourceFile upload(MultipartFile file, Long taskId, String sourceType) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new BizException("上传文件不能为空");
        }
        String originalName = file.getOriginalFilename();
        String ext = StringUtils.hasText(originalName) && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.'))
                : "";
        // objectKey：resource/{uuid}{ext}，LocalFileStorageService 会自动追加 yyyy/MM/dd 日期前缀
        String objectKey = "resource/" + IdUtil.fastSimpleUUID() + ext;
        String contentType = file.getContentType();

        String fullKey = storageService.upload(file.getInputStream(), objectKey, file.getSize(), contentType);
        log.info("文件上传成功 objectKey={}, size={}", fullKey, file.getSize());

        ResourceFile record = new ResourceFile();
        record.setTaskId(taskId);
        record.setUserId(SecurityUtils.getCurrentUserIdOrNull());
        record.setFileName(FileUtil.getName(fullKey));
        record.setOriginalName(originalName);
        record.setFileType(detectFileType(contentType));
        record.setMimeType(contentType);
        record.setFileSize(file.getSize());
        record.setBucketName(resolveBucket());
        record.setObjectKey(fullKey);
        record.setSourceType(StringUtils.hasText(sourceType) ? sourceType : "user_upload");
        record.setIsPublic(0);
        this.save(record);
        return record;
    }

    @Override
    public PageResult<ResourceFile> page(PageQuery query, Long taskId, Integer fileType) {
        LambdaQueryWrapper<ResourceFile> wrapper = new LambdaQueryWrapper<>();
        if (taskId != null) {
            wrapper.eq(ResourceFile::getTaskId, taskId);
        }
        if (fileType != null) {
            wrapper.eq(ResourceFile::getFileType, fileType);
        }
        wrapper.orderByDesc(ResourceFile::getCreateTime);
        Page<ResourceFile> page = new Page<>(query.getPage(), query.getSize());
        Page<ResourceFile> result = this.page(page, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public ResourceFile getById(Long id) {
        ResourceFile record = super.getById(id);
        if (record == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND);
        }
        return record;
    }

    @Override
    public void delete(Long id) throws Exception {
        ResourceFile record = super.getById(id);
        if (record == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND);
        }
        try {
            storageService.delete(record.getObjectKey());
        } catch (Exception e) {
            log.warn("物理删除文件失败 objectKey={}, 继续删除数据库记录: {}", record.getObjectKey(), e.getMessage());
        }
        this.removeById(id);
    }

    @Override
    public String signUrl(Long id, int expireSeconds) throws Exception {
        ResourceFile record = super.getById(id);
        if (record == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND);
        }
        String url = storageService.signUrl(record.getObjectKey(), expireSeconds);
        ResourceFile update = new ResourceFile();
        update.setId(id);
        update.setTempUrl(url);
        update.setTempUrlExpire(LocalDateTime.now().plusSeconds(expireSeconds));
        update.setFileUrl(url);
        this.updateById(update);
        return url;
    }

    /** 文件类型：1图片 2音频 3视频 4文档 5其他 */
    private Integer detectFileType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return 5;
        }
        if (contentType.startsWith("image/")) return 1;
        if (contentType.startsWith("audio/")) return 2;
        if (contentType.startsWith("video/")) return 3;
        if (contentType.contains("pdf") || contentType.contains("word") || contentType.contains("excel")
                || contentType.contains("text") || contentType.contains("json") || contentType.contains("xml")) {
            return 4;
        }
        return 5;
    }

    private String resolveBucket() {
        return "minio".equalsIgnoreCase(storageProperties.getType())
                ? storageProperties.getMinio().getBucket()
                : "local-storage";
    }
}
