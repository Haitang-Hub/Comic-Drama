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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComicWorkServiceImpl extends ServiceImpl<ComicWorkMapper, ComicWork> implements ComicWorkService {

    @Override
    public ComicWork createWork(Long taskId, String title, String coverUrl, String finalVideoUrl,
                                String resolution, Integer duration, Long userId) {
        ComicWork work = new ComicWork();
        work.setWorkNo("WK" + IdUtil.getSnowflakeNextIdStr());
        work.setTaskId(taskId);
        // 优先使用显式传入的 userId（跨服务调用），回退到登录上下文，最终回退到 1L
        Long effectiveUserId = userId != null ? userId : SecurityUtils.getCurrentUserIdOrNull();
        work.setUserId(effectiveUserId != null ? effectiveUserId : 1L);
        work.setTitle(title);
        work.setCoverUrl(coverUrl);
        work.setVideoUrl(finalVideoUrl);
        work.setResolution(resolution);
        work.setDuration(duration);
        work.setStatus(1);
        work.setIsPublic(0);
        work.setViewCount(0);
        work.setLikeCount(0);
        this.save(work);
        log.info("作品创建成功 workNo={}, taskId={}, userId={}", work.getWorkNo(), taskId, work.getUserId());
        return work;
    }

    @Override
    public ComicWork getByTaskId(Long taskId) {
        return this.getOne(new LambdaQueryWrapper<ComicWork>()
                .eq(ComicWork::getTaskId, taskId)
                .last("LIMIT 1"));
    }

    @Override
    public ComicWork getById(Long id) {
        ComicWork work = super.getById(id);
        if (work == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND);
        }
        return work;
    }

    @Override
    public void delete(Long id) {
        ComicWork work = super.getById(id);
        if (work == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND);
        }
        this.removeById(id);
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
        }
        if (userId != null) {
            wrapper.eq(ComicWork::getUserId, userId);
        }
        wrapper.orderByDesc(ComicWork::getCreateTime);
        Page<ComicWork> page = new Page<>(query.getPage(), query.getSize());
        Page<ComicWork> result = this.page(page, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }
}
