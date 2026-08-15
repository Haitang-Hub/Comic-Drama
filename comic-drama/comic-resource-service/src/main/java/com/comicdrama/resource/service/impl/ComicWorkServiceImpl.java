package com.comicdrama.resource.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.exception.BizException;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.common.result.ResultCode;
import com.comicdrama.common.util.SecurityUtils;
import com.comicdrama.resource.entity.ComicWork;
import com.comicdrama.resource.mapper.ComicWorkMapper;
import com.comicdrama.resource.service.ComicWorkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ComicWorkServiceImpl extends ServiceImpl<ComicWorkMapper, ComicWork> implements ComicWorkService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static String decodeTitle(String title) {
        if (!StringUtils.hasText(title)) return title;
        try {
            String decoded = URLDecoder.decode(title, StandardCharsets.UTF_8.name());
            if (!decoded.equals(title) || title.chars().filter(c -> c == '%').count() > 0) {
                return decoded;
            }
        } catch (Exception e) {
            log.warn("URL解码标题失败: {}", title);
        }
        return title;
    }

    private ComicWork hydrateExtraFields(ComicWork work) {
        if (work == null) return null;
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT zip_url, segment_count, merged_from, aspect_ratio, file_size, " +
                            "share_token, share_expire, publish_time FROM comic_work WHERE id = ?",
                    work.getId());
            if (row.containsKey("zip_url")) work.setZipUrl((String) row.get("zip_url"));
            if (row.containsKey("segment_count") && row.get("segment_count") != null) {
                work.setSegmentCount(((Number) row.get("segment_count")).intValue());
            }
            if (row.containsKey("merged_from")) work.setMergedFrom((String) row.get("merged_from"));
            if (row.containsKey("aspect_ratio")) work.setAspectRatio((String) row.get("aspect_ratio"));
            if (row.containsKey("file_size") && row.get("file_size") != null) {
                work.setFileSize(((Number) row.get("file_size")).longValue());
            }
            if (row.containsKey("share_token")) work.setShareToken((String) row.get("share_token"));
            if (row.containsKey("share_expire") && row.get("share_expire") != null) {
                work.setShareExpire((LocalDateTime) row.get("share_expire"));
            }
            if (row.containsKey("publish_time") && row.get("publish_time") != null) {
                work.setPublishTime((LocalDateTime) row.get("publish_time"));
            }
        } catch (Exception e) {
            log.debug("hydrateExtraFields 失败（新列可能尚未添加）: {}", e.getMessage());
        }
        return work;
    }

    private static ComicWork sanitizeWork(ComicWork work) {
        if (work != null) {
            work.setTitle(decodeTitle(work.getTitle()));
            if (work.getZipUrl() == null && StringUtils.hasText(work.getVideoUrl())
                    && work.getVideoUrl().toLowerCase().endsWith(".zip")) {
                work.setZipUrl(work.getVideoUrl());
            }
            if (work.getSegmentCount() == null) work.setSegmentCount(0);
            if (work.getFileSize() == null) work.setFileSize(0L);
            if (work.getViewCount() == null) work.setViewCount(0);
            if (work.getLikeCount() == null) work.setLikeCount(0);
            if (work.getIsPublic() == null) work.setIsPublic(0);
        }
        return work;
    }

    @Override
    public ComicWork createWork(Long taskId, String title, String coverUrl, String finalVideoUrl, String primaryVideoUrl,
                                String resolution, Integer duration, Long userId) {
        ComicWork work = new ComicWork();
        work.setWorkNo("WK" + IdUtil.getSnowflakeNextIdStr());
        work.setTaskId(taskId);
        Long effectiveUserId = userId != null ? userId : SecurityUtils.getCurrentUserIdOrNull();
        work.setUserId(effectiveUserId != null ? effectiveUserId : 1L);
        work.setTitle(title);
        work.setCoverUrl(coverUrl);
        work.setVideoUrl(StringUtils.hasText(primaryVideoUrl) ? primaryVideoUrl : finalVideoUrl);
        work.setResolution(resolution);
        work.setDuration(duration);
        work.setStatus(1);
        work.setIsPublic(0);
        work.setViewCount(0);
        work.setLikeCount(0);
        this.save(work);
        log.info("作品创建成功 workNo={}, taskId={}, userId={}, title={}",
                work.getWorkNo(), taskId, work.getUserId(), title);
        return work;
    }

    @Override
    public ComicWork getByTaskId(Long taskId) {
        ComicWork work = this.getOne(new LambdaQueryWrapper<ComicWork>()
                .eq(ComicWork::getTaskId, taskId)
                .last("LIMIT 1"));
        return work != null ? sanitizeWork(hydrateExtraFields(work)) : null;
    }

    @Override
    public ComicWork getById(Long id) {
        ComicWork work = super.getById(id);
        if (work == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND);
        }
        return sanitizeWork(hydrateExtraFields(work));
    }

    @Override
    public PageResult<ComicWork> page(PageQuery query, String keyword, Integer status, Long userId) {
        LambdaQueryWrapper<ComicWork> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(ComicWork::getWorkNo, keyword)
                    .or().like(ComicWork::getTitle, keyword);
        }
        if (status != null) {
            wrapper.eq(ComicWork::getStatus, status);
        } else {
            wrapper.eq(ComicWork::getStatus, 1);
        }
        if (userId != null) {
            wrapper.eq(ComicWork::getUserId, userId);
        }
        wrapper.orderByDesc(ComicWork::getCreateTime);
        Page<ComicWork> page = new Page<>(query.getPage(), query.getSize());
        Page<ComicWork> result = this.page(page, wrapper);
        List<ComicWork> records = result.getRecords().stream()
                .map(this::hydrateExtraFields)
                .map(ComicWorkServiceImpl::sanitizeWork)
                .collect(Collectors.toList());
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public String generateShareToken(Long id, int expireHours) {
        ComicWork work = super.getById(id);
        if (work == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND);
        }
        String token = UUID.randomUUID().toString().replace("-", "") + IdUtil.getSnowflakeNextIdStr();
        LocalDateTime expire = LocalDateTime.now().plusHours(expireHours);
        try {
            jdbcTemplate.update(
                    "UPDATE comic_work SET share_token = ?, share_expire = ? WHERE id = ?",
                    token, expire, id);
        } catch (Exception e) {
            log.warn("写入分享令牌失败（share_token 列可能尚未添加）: {}", e.getMessage());
            throw new BizException("数据库尚未添加 share_token 列，请先执行 SQL 迁移脚本");
        }
        work.setShareToken(token);
        work.setShareExpire(expire);
        return token;
    }

    @Override
    public ComicWork getByShareToken(String token) {
        if (!StringUtils.hasText(token)) return null;
        try {
            List<ComicWork> works = jdbcTemplate.query(
                    "SELECT id, work_no, task_id, user_id, title, cover_url, video_url, " +
                            "duration, resolution, status, is_public, view_count, like_count " +
                            "FROM comic_work WHERE share_token = ? AND share_expire > NOW() LIMIT 1",
                    (rs, rowNum) -> {
                        ComicWork w = new ComicWork();
                        w.setId(rs.getLong("id"));
                        w.setWorkNo(rs.getString("work_no"));
                        w.setTaskId(rs.getLong("task_id"));
                        w.setUserId(rs.getLong("user_id"));
                        w.setTitle(rs.getString("title"));
                        w.setCoverUrl(rs.getString("cover_url"));
                        w.setVideoUrl(rs.getString("video_url"));
                        w.setDuration(rs.getInt("duration"));
                        w.setResolution(rs.getString("resolution"));
                        w.setStatus(rs.getInt("status"));
                        w.setIsPublic(rs.getInt("is_public"));
                        w.setViewCount(rs.getInt("view_count"));
                        w.setLikeCount(rs.getInt("like_count"));
                        return w;
                    }, token);
            if (!works.isEmpty()) {
                ComicWork work = works.get(0);
                jdbcTemplate.update("UPDATE comic_work SET view_count = view_count + 1 WHERE id = ?", work.getId());
                work.setViewCount((work.getViewCount() == null ? 0 : work.getViewCount()) + 1);
                return sanitizeWork(hydrateExtraFields(work));
            }
        } catch (Exception e) {
            log.warn("按分享令牌查询失败（share_token 列可能尚未添加）: {}", e.getMessage());
        }
        return null;
    }

    @Override
    public void incrementViewCount(Long id) {
        try {
            jdbcTemplate.update("UPDATE comic_work SET view_count = COALESCE(view_count, 0) + 1 WHERE id = ?", id);
        } catch (Exception e) {
            log.warn("浏览量更新失败: {}", e.getMessage());
        }
    }
}
