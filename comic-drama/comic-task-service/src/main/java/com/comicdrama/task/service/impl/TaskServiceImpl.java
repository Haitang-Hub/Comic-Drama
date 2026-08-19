package com.comicdrama.task.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.exception.BizException;
import com.comicdrama.common.queue.TaskQueueEntry;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.common.result.ResultCode;
import com.comicdrama.common.util.SecurityUtils;
import com.comicdrama.common.dto.TaskCreateDTO;
import com.comicdrama.task.config.RestTemplateConfig;
import com.comicdrama.task.entity.ComicTask;
import com.comicdrama.task.entity.TaskProgressLog;
import com.comicdrama.task.entity.TaskQueue;
import com.comicdrama.task.mapper.ComicTaskMapper;
import com.comicdrama.task.mapper.TaskProgressLogMapper;
import com.comicdrama.task.mapper.TaskQueueMapper;
import com.comicdrama.task.service.TaskService;
import com.comicdrama.task.vo.TaskDetailVO;
import com.comicdrama.common.enums.TaskStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskServiceImpl extends ServiceImpl<ComicTaskMapper, ComicTask> implements TaskService {

    @Override
    public ComicTaskMapper getBaseMapper() {
        return super.getBaseMapper();
    }

    private final TaskQueueMapper taskQueueMapper;
    private final TaskProgressLogMapper taskProgressLogMapper;
    private final JdbcTemplate jdbcTemplate;
    // 注意：本类同时引用 common.queue.TaskQueue（队列抽象）与 entity.TaskQueue（队列记录），
    // 二者简单名相同，故抽象接口用全限定名声明，实体用简单名 import。
    private final com.comicdrama.common.queue.TaskQueue taskQueue;
    private final RestTemplate restTemplate;
    private final RestTemplateConfig restTemplateConfig;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ComicTask createTask(TaskCreateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();

        ComicTask task = new ComicTask();
        BeanUtils.copyProperties(dto, task);
        task.setTaskNo(generateTaskNo());
        task.setUserId(userId);
        task.setStatus(TaskStatus.QUEUE.getCode());
        task.setCurrentStep(0);
        task.setProgress(0);
        task.setRetryCount(0);
        // 队列优先级：默认 100，VIP/加急可扩展
        Integer priority = 100;
        task.setRemark(dto.getRemark());
        this.save(task);

        // 写 task_queue（等待中）
        TaskQueue queue = new TaskQueue();
        queue.setTaskId(task.getId());
        queue.setUserId(userId);
        queue.setQueueStatus(0);
        queue.setPriority(priority);
        queue.setEnqueuedTime(LocalDateTime.now());
        taskQueueMapper.insert(queue);

        // 入内存队列（stub）
        TaskQueueEntry entry = TaskQueueEntry.builder()
                .taskId(task.getId())
                .userId(userId)
                .priority(priority)
                .enqueuedTime(LocalDateTime.now())
                .build();
        taskQueue.enqueue(entry);
        int position = taskQueue.getPosition(task.getId());
        // 回填排队位置
        ComicTask posUpdate = new ComicTask();
        posUpdate.setId(task.getId());
        posUpdate.setQueuePosition(position);
        this.updateById(posUpdate);
        TaskQueue qUpdate = new TaskQueue();
        qUpdate.setId(queue.getId());
        qUpdate.setQueuePosition(position);
        taskQueueMapper.updateById(qUpdate);

        // 写进度日志：任务已创建
        writeProgressLog(task.getId(), 0, null, null, 0, 0, "任务已创建，排队中（位置 " + position + "）");

        log.info("任务创建成功 taskId={}, taskNo={}, userId={}, 队列位置={}", task.getId(), task.getTaskNo(), userId, position);
        return task;
    }

    @Override
    public PageResult<ComicTask> page(PageQuery query, String keyword, Integer status, boolean queryAll) {
        LambdaQueryWrapper<ComicTask> wrapper = new LambdaQueryWrapper<>();
        // 非管理员仅看自己的任务
        boolean isAdmin = false;
        try {
            isAdmin = cn.dev33.satoken.stp.StpUtil.hasRole("ADMIN");
        } catch (Exception ignored) {
        }
        if (!queryAll || !isAdmin) {
            Long uid = SecurityUtils.getCurrentUserIdOrNull();
            if (uid != null) {
                wrapper.eq(ComicTask::getUserId, uid);
            }
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(ComicTask::getTaskNo, keyword)
                    .or().like(ComicTask::getTitle, keyword));
        }
        if (status != null) {
            wrapper.eq(ComicTask::getStatus, status);
        }
        wrapper.orderByDesc(ComicTask::getCreateTime);
        Page<ComicTask> page = new Page<>(query.getPage(), query.getSize());
        Page<ComicTask> result = this.page(page, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public TaskDetailVO getDetail(Long id) {
        ComicTask task = this.getById(id);
        if (task == null) {
            throw new BizException(ResultCode.TASK_NOT_FOUND);
        }

        TaskDetailVO vo = new TaskDetailVO();
        // 拷贝任务基本信息
        vo.setId(task.getId());
        vo.setTaskNo(task.getTaskNo());
        vo.setTitle(task.getTitle());
        vo.setStoryRequirement(task.getStoryRequirement());
        vo.setStatus(task.getStatus());
        TaskStatus statusEnum = TaskStatus.of(task.getStatus());
        vo.setStatusText(statusEnum != null ? statusEnum.getDesc() : "未知");
        vo.setCurrentStep(task.getCurrentStep());
        vo.setProgress(task.getProgress());
        vo.setDuration(task.getDuration());
        vo.setAspectRatio(task.getAspectRatio());
        vo.setResolution(task.getResolution());
        vo.setVoiceEnabled(task.getVoiceEnabled());
        vo.setExecMode(task.getExecMode());
        vo.setArtStyle(task.getArtStyle());
        vo.setVisualStyle(task.getVisualStyle());
        vo.setFailureStep(task.getFailureStep());
        vo.setFailureReason(task.getFailureReason());
        vo.setFailureDetail(task.getFailureDetail());
        vo.setCreateTime(task.getCreateTime() != null ? task.getCreateTime().toString() : null);
        vo.setStartTime(task.getStartTime() != null ? task.getStartTime().toString() : null);
        vo.setEndTime(task.getEndTime() != null ? task.getEndTime().toString() : null);
        vo.setTotalConsumeTime(task.getTotalConsumeTime());
        vo.setFinalVideoUrl(task.getFinalVideoUrl());
        vo.setCoverUrl(task.getCoverUrl());
        vo.setFinalWorkManifest(task.getFinalWorkManifest());
        vo.setOutline(queryStorySummary(id));
        vo.setStoryboards(queryStoryboards(id));
        vo.setAssetDesigns(queryAssetDesigns(id));
        // 步骤4 / 步骤5 产物分开：首版图和衍生图独立展示，便于用户单独重新生成某一步
        vo.setAssetImages(queryBaseAssetImages(id));
        vo.setDeriveImages(queryDerivedAssetImages(id));
        vo.setImages(queryStoryboardImages(id));
        vo.setAudios(queryStoryboardAudios(id));
        vo.setVideos(querySceneVideos(id));

        // 查询场景分组（从 storyboard 的 group_id 派生）
        vo.setSceneGroups(querySceneGroups(id));

        // 查询进度日志
        vo.setProgressLogs(queryProgressLogs(id));
        // 查询节点状态
        vo.setNodeStates(queryNodeStates(id));
        // 查询失败日志
        vo.setFailureLogs(queryFailureLogs(id));

        // 计算是否处于"审核暂停"态：人工审核模式下，当前步骤已完成但任务暂停等待审核
        vo.setPendingReview(computePendingReview(task, vo.getNodeStates()));

        return vo;
    }

    /**
     * 判断任务是否处于"审核暂停"态。
     * 条件：execMode=1（人工审核）&& status=PAUSED(4) && 当前步骤的 node_state.status=2（已完成）
     */
    private Boolean computePendingReview(ComicTask task, List<TaskDetailVO.TaskNodeStateVO> nodeStates) {
        if (task.getExecMode() == null || task.getExecMode() != 1) return false;
        if (task.getStatus() == null || task.getStatus() != TaskStatus.PAUSED.getCode()) return false;
        Integer curStep = task.getCurrentStep();
        if (curStep == null || curStep <= 0 || nodeStates == null) return false;
        return nodeStates.stream()
                .filter(n -> curStep.equals(n.getStep()))
                .anyMatch(n -> n.getStatus() != null && n.getStatus() == 2);
    }

    private TaskDetailVO.StoryOutlineVO queryStorySummary(Long taskId) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, content, duration, create_time FROM story_summary WHERE task_id = ? ORDER BY id DESC LIMIT 1",
                taskId);
            if (!rows.isEmpty()) {
                Map<String, Object> row = rows.get(0);
                TaskDetailVO.StoryOutlineVO vo = new TaskDetailVO.StoryOutlineVO();
                vo.setId(((Number) row.get("id")).longValue());
                String content = (String) row.get("content");
                vo.setOutlineText(content);
                vo.setSummary(content != null && content.length() > 100 ? content.substring(0, 100) + "..." : content);
                vo.setWordCount(content != null ? content.length() : 0);
                vo.setCreateTime(row.get("create_time") != null ? row.get("create_time").toString() : null);
                return vo;
            }
        } catch (Exception e) {
            log.warn("查询故事摘要失败 taskId={}", taskId, e);
        }
        return null;
    }

    private List<TaskDetailVO.StoryboardVO> queryStoryboards(Long taskId) {
        List<TaskDetailVO.StoryboardVO> result = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, seq, local_seq, duration, group_id, camera_angle, `character`, dialogue, shot_desc, storyboard_desc, visual_desc, scene, props, create_time FROM storyboard WHERE task_id = ? ORDER BY group_id, local_seq",
                taskId);
            for (Map<String, Object> row : rows) {
                TaskDetailVO.StoryboardVO vo = new TaskDetailVO.StoryboardVO();
                vo.setId(((Number) row.get("id")).longValue());
                vo.setSceneIndex(row.get("seq") != null ? ((Number) row.get("seq")).intValue() : null);
                vo.setLocalSeq(row.get("local_seq") != null ? ((Number) row.get("local_seq")).intValue() : null);
                vo.setDuration(row.get("duration") != null ? ((Number) row.get("duration")).intValue() : null);
                vo.setSceneGroupId(row.get("group_id") != null ? ((Number) row.get("group_id")).longValue() : null);
                vo.setCameraAngle((String) row.get("camera_angle"));
                vo.setCharacters((String) row.get("character"));
                vo.setDialogue((String) row.get("dialogue"));
                vo.setShotDesc((String) row.get("shot_desc"));
                vo.setStoryboardDesc((String) row.get("storyboard_desc"));
                vo.setVisualDesc((String) row.get("visual_desc"));
                vo.setScene((String) row.get("scene"));
                vo.setProps((String) row.get("props"));
                vo.setCreateTime(row.get("create_time") != null ? row.get("create_time").toString() : null);
                result.add(vo);
            }
        } catch (Exception e) {
            log.warn("查询分镜脚本失败 taskId={}", taskId, e);
        }
        return result;
    }

    private List<TaskDetailVO.SceneGroupVO> querySceneGroups(Long taskId) {
        List<TaskDetailVO.SceneGroupVO> result = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT group_id, COUNT(*) as scene_count, SUM(duration) as total_duration FROM storyboard WHERE task_id = ? GROUP BY group_id ORDER BY group_id",
                taskId);
            for (Map<String, Object> row : rows) {
                TaskDetailVO.SceneGroupVO vo = new TaskDetailVO.SceneGroupVO();
                Integer groupId = row.get("group_id") != null ? ((Number) row.get("group_id")).intValue() : 0;
                vo.setGroupIndex(groupId);
                vo.setTitle("场景 " + groupId);
                vo.setSceneCount(row.get("scene_count") != null ? ((Number) row.get("scene_count")).intValue() : 0);
                vo.setDuration(row.get("total_duration") != null ? ((Number) row.get("total_duration")).intValue() : 0);
                result.add(vo);
            }
        } catch (Exception e) {
            log.warn("查询场景分组失败 taskId={}", taskId, e);
        }
        return result;
    }

    private List<TaskDetailVO.AssetDesignVO> queryAssetDesigns(Long taskId) {
        List<TaskDetailVO.AssetDesignVO> result = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, asset_type, base_asset_name, derived_from, version, asset_name, asset_desc, resource_url, create_time FROM asset_design WHERE task_id = ? ORDER BY asset_type, asset_name",
                taskId);
            for (Map<String, Object> row : rows) {
                TaskDetailVO.AssetDesignVO vo = new TaskDetailVO.AssetDesignVO();
                vo.setId(((Number) row.get("id")).longValue());
                vo.setAssetType((String) row.get("asset_type"));
                vo.setBaseAssetName((String) row.get("base_asset_name"));
                vo.setDerivedFrom((String) row.get("derived_from"));
                vo.setAssetName((String) row.get("asset_name"));
                vo.setAssetDesc((String) row.get("asset_desc"));
                vo.setResourceUrl((String) row.get("resource_url"));
                vo.setVersion(row.get("version") != null ? ((Number) row.get("version")).intValue() : 1);
                vo.setCreateTime(row.get("create_time") != null ? row.get("create_time").toString() : null);
                result.add(vo);
            }
        } catch (Exception e) {
            log.warn("查询资产设计失败 taskId={}", taskId, e);
        }
        return result;
    }

    /** 步骤4：查询首版资产图（base_image_id IS NULL） */
    private List<TaskDetailVO.AssetImageVO> queryBaseAssetImages(Long taskId) {
        List<TaskDetailVO.AssetImageVO> result = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, asset_id, asset_type, asset_name, image_url, thumbnail_url, width, height, prompt_used, status, create_time " +
                    "FROM asset_image WHERE task_id = ? AND base_image_id IS NULL ORDER BY asset_type, asset_name",
                taskId);
            for (Map<String, Object> row : rows) {
                TaskDetailVO.AssetImageVO vo = new TaskDetailVO.AssetImageVO();
                vo.setId(((Number) row.get("id")).longValue());
                vo.setAssetId(row.get("asset_id") != null ? ((Number) row.get("asset_id")).longValue() : null);
                vo.setAssetType((String) row.get("asset_type"));
                vo.setAssetName((String) row.get("asset_name"));
                vo.setImageUrl((String) row.get("image_url"));
                vo.setThumbnailUrl((String) row.get("thumbnail_url"));
                vo.setWidth(row.get("width") != null ? ((Number) row.get("width")).intValue() : null);
                vo.setHeight(row.get("height") != null ? ((Number) row.get("height")).intValue() : null);
                vo.setPromptUsed((String) row.get("prompt_used"));
                vo.setStatus(row.get("status") != null ? ((Number) row.get("status")).intValue() : null);
                vo.setCreateTime(row.get("create_time") != null ? row.get("create_time").toString() : null);
                result.add(vo);
            }
        } catch (Exception e) {
            log.warn("查询首版资产图片(步骤4)失败 taskId={}", taskId, e);
        }
        return result;
    }

    /** 步骤5：查询衍生资产图（base_image_id IS NOT NULL） */
    private List<TaskDetailVO.AssetImageVO> queryDerivedAssetImages(Long taskId) {
        List<TaskDetailVO.AssetImageVO> result = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, asset_id, asset_type, asset_name, image_url, thumbnail_url, width, height, prompt_used, status, create_time " +
                    "FROM asset_image WHERE task_id = ? AND base_image_id IS NOT NULL ORDER BY asset_type, asset_name",
                taskId);
            for (Map<String, Object> row : rows) {
                TaskDetailVO.AssetImageVO vo = new TaskDetailVO.AssetImageVO();
                vo.setId(((Number) row.get("id")).longValue());
                vo.setAssetId(row.get("asset_id") != null ? ((Number) row.get("asset_id")).longValue() : null);
                vo.setAssetType((String) row.get("asset_type"));
                vo.setAssetName((String) row.get("asset_name"));
                vo.setImageUrl((String) row.get("image_url"));
                vo.setThumbnailUrl((String) row.get("thumbnail_url"));
                vo.setWidth(row.get("width") != null ? ((Number) row.get("width")).intValue() : null);
                vo.setHeight(row.get("height") != null ? ((Number) row.get("height")).intValue() : null);
                vo.setPromptUsed((String) row.get("prompt_used"));
                vo.setStatus(row.get("status") != null ? ((Number) row.get("status")).intValue() : null);
                vo.setCreateTime(row.get("create_time") != null ? row.get("create_time").toString() : null);
                result.add(vo);
            }
        } catch (Exception e) {
            log.warn("查询衍生资产图片(步骤5)失败 taskId={}", taskId, e);
        }
        return result;
    }

    private List<TaskDetailVO.StoryboardImageVO> queryStoryboardImages(Long taskId) {
        List<TaskDetailVO.StoryboardImageVO> result = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT si.id, si.storyboard_id, sb.seq as scene_index, si.image_url, si.thumbnail_url, si.width, si.height, si.prompt_used, si.create_time FROM storyboard_image si LEFT JOIN storyboard sb ON si.storyboard_id = sb.id WHERE si.task_id = ? ORDER BY sb.seq",
                taskId);
            for (Map<String, Object> row : rows) {
                TaskDetailVO.StoryboardImageVO vo = new TaskDetailVO.StoryboardImageVO();
                vo.setId(((Number) row.get("id")).longValue());
                vo.setSceneIndex(row.get("scene_index") != null ? ((Number) row.get("scene_index")).intValue() : null);
                vo.setImageUrl((String) row.get("image_url"));
                vo.setThumbnailUrl((String) row.get("thumbnail_url"));
                vo.setWidth(row.get("width") != null ? ((Number) row.get("width")).intValue() : null);
                vo.setHeight(row.get("height") != null ? ((Number) row.get("height")).intValue() : null);
                vo.setPrompt((String) row.get("prompt_used"));
                vo.setCreateTime(row.get("create_time") != null ? row.get("create_time").toString() : null);
                result.add(vo);
            }
        } catch (Exception e) {
            log.warn("查询分镜图片失败 taskId={}", taskId, e);
        }
        return result;
    }

    private List<TaskDetailVO.StoryboardAudioVO> queryStoryboardAudios(Long taskId) {
        List<TaskDetailVO.StoryboardAudioVO> result = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT sa.id, sa.storyboard_id, sb.seq as scene_index, sa.audio_url, sa.duration, sa.emotion, sa.speed, sa.create_time FROM storyboard_audio sa LEFT JOIN storyboard sb ON sa.storyboard_id = sb.id WHERE sa.task_id = ? ORDER BY sb.seq",
                taskId);
            for (Map<String, Object> row : rows) {
                TaskDetailVO.StoryboardAudioVO vo = new TaskDetailVO.StoryboardAudioVO();
                vo.setId(((Number) row.get("id")).longValue());
                vo.setSceneIndex(row.get("scene_index") != null ? ((Number) row.get("scene_index")).intValue() : null);
                vo.setAudioUrl((String) row.get("audio_url"));
                Object duration = row.get("duration");
                if (duration instanceof Number) {
                    vo.setDuration(((Number) duration).intValue());
                }
                vo.setEmotion((String) row.get("emotion"));
                vo.setSpeed(row.get("speed") != null ? ((Number) row.get("speed")).intValue() : null);
                vo.setCreateTime(row.get("create_time") != null ? row.get("create_time").toString() : null);
                result.add(vo);
            }
        } catch (Exception e) {
            log.warn("查询分镜音频失败 taskId={}", taskId, e);
        }
        return result;
    }

    private List<TaskDetailVO.SceneVideoVO> querySceneVideos(Long taskId) {
        List<TaskDetailVO.SceneVideoVO> result = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, scene_group_id, video_url, thumbnail_url, duration, resolution, create_time FROM scene_video WHERE task_id = ? ORDER BY scene_group_id",
                taskId);
            for (Map<String, Object> row : rows) {
                TaskDetailVO.SceneVideoVO vo = new TaskDetailVO.SceneVideoVO();
                vo.setId(((Number) row.get("id")).longValue());
                vo.setSceneIndex(row.get("scene_group_id") != null ? ((Number) row.get("scene_group_id")).intValue() : null);
                vo.setVideoUrl((String) row.get("video_url"));
                vo.setCoverUrl((String) row.get("thumbnail_url"));
                Object duration = row.get("duration");
                if (duration instanceof Number) {
                    vo.setDuration(((Number) duration).intValue());
                }
                vo.setResolution((String) row.get("resolution"));
                vo.setCreateTime(row.get("create_time") != null ? row.get("create_time").toString() : null);
                result.add(vo);
            }
        } catch (Exception e) {
            log.warn("查询场景视频失败 taskId={}", taskId, e);
        }
        return result;
    }

    private List<TaskDetailVO.TaskProgressLogVO> queryProgressLogs(Long taskId) {
        List<TaskDetailVO.TaskProgressLogVO> result = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, task_id, step, node_type, progress, total_progress, status, message, create_time FROM task_progress_log WHERE task_id = ? ORDER BY create_time DESC LIMIT 100",
                taskId);
            for (Map<String, Object> row : rows) {
                TaskDetailVO.TaskProgressLogVO vo = new TaskDetailVO.TaskProgressLogVO();
                vo.setId(row.get("id") != null ? row.get("id").toString() : null);
                vo.setTaskId(row.get("task_id") != null ? ((Number) row.get("task_id")).longValue() : null);
                vo.setStep(row.get("step") != null ? ((Number) row.get("step")).intValue() : null);
                vo.setProgress(row.get("progress") != null ? ((Number) row.get("progress")).intValue() : null);
                vo.setStatus(row.get("status") != null ? ((Number) row.get("status")).intValue() : null);
                vo.setMessage((String) row.get("message"));
                vo.setCreateTime(row.get("create_time") != null ? row.get("create_time").toString() : null);
                result.add(vo);
            }
        } catch (Exception e) {
            log.warn("查询进度日志失败 taskId={}", taskId, e);
        }
        return result;
    }

    private List<TaskDetailVO.TaskNodeStateVO> queryNodeStates(Long taskId) {
        List<TaskDetailVO.TaskNodeStateVO> result = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, task_id, node_type as node_code, node_name, step, node_status as status, start_time, end_time, duration_ms, error_msg as error_message, create_time FROM task_node_state WHERE task_id = ? ORDER BY step",
                taskId);
            for (Map<String, Object> row : rows) {
                TaskDetailVO.TaskNodeStateVO vo = new TaskDetailVO.TaskNodeStateVO();
                vo.setId(row.get("id") != null ? row.get("id").toString() : null);
                vo.setTaskId(row.get("task_id") != null ? ((Number) row.get("task_id")).longValue() : null);
                vo.setNodeCode((String) row.get("node_code"));
                vo.setNodeName((String) row.get("node_name"));
                vo.setStep(row.get("step") != null ? ((Number) row.get("step")).intValue() : null);
                vo.setStatus(row.get("status") != null ? ((Number) row.get("status")).intValue() : null);
                vo.setStartTime(row.get("start_time") != null ? row.get("start_time").toString() : null);
                vo.setEndTime(row.get("end_time") != null ? row.get("end_time").toString() : null);

                // duration_ms 存的是毫秒（由 saveNodeState 写入的 System.currentTimeMillis() 差值）。
                // 分拆为两个字段：durationMs（原始毫秒，用于前端需要高精度展示）、
                // duration（转秒取整，用于常规显示 "Xs"）。避免截图里那种"数字没单位换行又一个s"的混乱。
                if (row.get("duration_ms") != null) {
                    long ms = ((Number) row.get("duration_ms")).longValue();
                    vo.setDurationMs((int) Math.min(ms, Integer.MAX_VALUE));
                    vo.setDuration((int) Math.round(ms / 1000.0));
                }

                vo.setErrorMessage((String) row.get("error_message"));
                vo.setCreateTime(row.get("create_time") != null ? row.get("create_time").toString() : null);
                result.add(vo);
            }
        } catch (Exception e) {
            log.warn("查询节点状态失败 taskId={}", taskId, e);
        }
        return result;
    }

    private List<TaskDetailVO.TaskFailureLogVO> queryFailureLogs(Long taskId) {
        List<TaskDetailVO.TaskFailureLogVO> result = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, task_id, step, node_type, error_message, error_stack, create_time FROM task_failure_log WHERE task_id = ? ORDER BY create_time DESC",
                taskId);
            for (Map<String, Object> row : rows) {
                TaskDetailVO.TaskFailureLogVO vo = new TaskDetailVO.TaskFailureLogVO();
                vo.setId(row.get("id") != null ? row.get("id").toString() : null);
                vo.setTaskId(row.get("task_id") != null ? ((Number) row.get("task_id")).longValue() : null);
                Integer step = row.get("step") != null ? ((Number) row.get("step")).intValue() : null;
                vo.setStep(step);
                vo.setStepName(step != null ? "步骤" + step : null);
                vo.setNodeType((String) row.get("node_type"));
                vo.setErrorMessage((String) row.get("error_message"));
                vo.setErrorStack((String) row.get("error_stack"));
                vo.setCreateTime(row.get("create_time") != null ? row.get("create_time").toString() : null);
                result.add(vo);
            }
        } catch (Exception e) {
            log.warn("查询失败日志失败 taskId={}", taskId, e);
        }
        return result;
    }

    @Override
    public void pause(Long id) {
        // 默认：完成此阶段（保留产物、等当前步跑完后停），向后兼容旧调用方
        pause(id, false, true);
    }

    @Override
    public void pause(Long id, boolean rollbackCurrentStep) {
        if (rollbackCurrentStep) {
            pause(id, true, false);
        } else {
            pause(id, false, true); // 非回退默认走「完成此阶段」
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pause(Long id, boolean rollbackCurrentStep, boolean stopAfterCurrentStep) {
        ComicTask task = this.getById(id);
        if (task == null) {
            throw new BizException(ResultCode.TASK_NOT_FOUND);
        }
        Integer st = task.getStatus();
        // 仅排队/生成中可暂停
        if (st != null && st != TaskStatus.QUEUE.getCode() && st != TaskStatus.RUNNING.getCode()) {
            throw new BizException(ResultCode.TASK_STATUS_ILLEGAL, "当前状态不允许暂停");
        }
        // 从内存队列移除（若在队中，避免继续被Runner消费）
        taskQueue.remove(id);

        int currentStep = task.getCurrentStep() == null ? 0 : task.getCurrentStep();
        Integer finalProgress = task.getProgress();

        // ---- 语义 1：回退当前步骤 ----
        if (rollbackCurrentStep && currentStep > 0) {
            log.info("[pause-回退] taskId={}, 回退当前步={}（删除产物并重置状态）", id, currentStep);
            // 1. 级联删除 [currentStep, 9] 所有半成品产物（当前步骤生成到一半的内容必须清理）
            try {
                String url = restTemplateConfig.getWorkflowServiceUrl()
                        + "/api/workflow/pipeline/clean-artifacts?fromStep=" + currentStep + "&toStep=9";
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                java.util.Map<String, Object> body = new java.util.HashMap<>();
                body.put("taskId", id);
                HttpEntity<java.util.Map<String, Object>> entity = new HttpEntity<>(body, headers);

                ResponseEntity<com.comicdrama.common.result.Result<Void>> resp = restTemplate.exchange(
                        url, HttpMethod.POST, entity,
                        new ParameterizedTypeReference<com.comicdrama.common.result.Result<Void>>() {});
                if (resp.getBody() != null && resp.getBody().getCode() != 0 && resp.getBody().getCode() != 200) {
                    log.warn("[pause-回退] 清理产物失败 taskId={}, resp={}", id, resp.getBody());
                }
            } catch (Exception e) {
                log.warn("[pause-回退] 调用 workflow-service 清理产物失败 taskId={}", id, e);
            }

            // 2. 重置 [currentStep, 9] 的 node_state（回退节点状态为等待）
            try {
                jdbcTemplate.update(
                        "UPDATE task_node_state SET node_status = 0, start_time = NULL, end_time = NULL, " +
                                "duration_ms = NULL, node_data = NULL, remark = NULL " +
                                "WHERE task_id = ? AND step >= ?",
                        id, currentStep);
            } catch (Exception e) {
                log.warn("[pause-回退] 重置 node_state 失败 taskId={}", id, e);
            }

            // 3. currentStep 回退到上一步，下次继续时会从 currentStep+1 = 原 currentStep 重新执行
            int rolledBackStep = Math.max(0, currentStep - 1);
            writeProgressLog(id, currentStep, null, null, finalProgress, finalProgress,
                    "任务已暂停（回退，从步骤 " + currentStep + " 撤销到步骤 " + rolledBackStep + "）");
            currentStep = rolledBackStep;

            // 4. 立即标记 PAUSED
            ComicTask update = new ComicTask();
            update.setId(id);
            update.setStatus(TaskStatus.PAUSED.getCode());
            update.setCurrentStep(currentStep > 0 ? currentStep : null);
            this.updateById(update);

            TaskQueue qUpdate = new TaskQueue();
            qUpdate.setQueueStatus(3);
            taskQueueMapper.update(qUpdate, new LambdaQueryWrapper<TaskQueue>().eq(TaskQueue::getTaskId, id));
            return;
        }

        // ---- 语义 2：完成此阶段（stopAfterCurrent = true） ----
        //       不立刻标记 PAUSED，设置「计划暂停」标记让 workflow-service 在当前步骤执行完毕的边界处自动停下，
        //       保留当前步完整产物（用户想基于完整产物审核后再继续）。
        if (stopAfterCurrentStep) {
            log.info("[pause-完成此阶段] taskId={}, 设置计划暂停标记（等当前步执行完毕后自动暂停）", id);
            // 调用 workflow-service 的 TaskStateManager 设置计划暂停（通过暴露的 HTTP 接口）
            try {
                String url = restTemplateConfig.getWorkflowServiceUrl()
                        + "/api/workflow/pipeline/set-planned-pause?taskId=" + id
                        + "&expireMinutes=180";
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                ResponseEntity<com.comicdrama.common.result.Result<Void>> resp = restTemplate.exchange(
                        url, HttpMethod.POST, new HttpEntity<>(null, headers),
                        new ParameterizedTypeReference<com.comicdrama.common.result.Result<Void>>() {});
                if (resp.getBody() != null && resp.getBody().getCode() != 0 && resp.getBody().getCode() != 200) {
                    log.warn("[pause-完成此阶段] 设置计划暂停失败 taskId={}, resp={}", id, resp.getBody());
                    // 降级为立即暂停
                    immediateMarkPaused(id, currentStep, finalProgress,
                            "任务已暂停（设置计划暂停失败，降级为立即暂停，保留产物）");
                    return;
                }
                writeProgressLog(id, currentStep, null, null, finalProgress, finalProgress,
                        "已设置完成此阶段：步骤执行完毕后自动暂停（保留步骤 " + currentStep + " 的产物）");
            } catch (Exception e) {
                log.warn("[pause-完成此阶段] 调用 workflow-service 设置计划暂停异常 taskId={}", id, e);
                immediateMarkPaused(id, currentStep, finalProgress,
                        "任务已暂停（设置计划暂停异常，降级为立即暂停，保留产物）");
            }
            return;
        }

        // ---- 语义 3：立即暂停（保留产物，不等待） ----
        immediateMarkPaused(id, currentStep, finalProgress,
                "任务已暂停（立即暂停，保留步骤 " + currentStep + " 的产物）");
    }

    /**
     * 立即将任务标记为 PAUSED（不做任何清理 / 不设置计划暂停），
     * 只更新 comic_task.status / currentStep 和 task_queue.queue_status。
     */
    private void immediateMarkPaused(Long id, int currentStep, Integer finalProgress, String remark) {
        ComicTask update = new ComicTask();
        update.setId(id);
        update.setStatus(TaskStatus.PAUSED.getCode());
        update.setCurrentStep(currentStep > 0 ? currentStep : null);
        this.updateById(update);

        TaskQueue qUpdate = new TaskQueue();
        qUpdate.setQueueStatus(3);
        taskQueueMapper.update(qUpdate, new LambdaQueryWrapper<TaskQueue>().eq(TaskQueue::getTaskId, id));

        writeProgressLog(id, currentStep, null, null, finalProgress, finalProgress, remark);
        log.info("[pause-立即暂停] taskId={}, currentStep={}", id, currentStep);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resume(Long id) {
        ComicTask task = this.getById(id);
        if (task == null) {
            throw new BizException(ResultCode.TASK_NOT_FOUND);
        }
        if (task.getStatus() == null || task.getStatus() != TaskStatus.PAUSED.getCode()) {
            throw new BizException(ResultCode.TASK_STATUS_ILLEGAL, "仅已暂停任务可恢复");
        }

        int currentStep = task.getCurrentStep() == null ? 0 : task.getCurrentStep();
        // 【P4】DB 级 findResumeStep 兜底：不盲信 comic_task.current_step。
        // 查 task_node_state 中第一个非 DONE(8) / 非 PAUSED(5) 的步骤，避免 current_step=0 时从头重跑浪费产出。
        int dbResumeStep = findResumeStepFromDb(id);
        int startStep;
        if (dbResumeStep > 0) {
            startStep = dbResumeStep;
            if (startStep != Math.max(1, currentStep)) {
                log.warn("resume: current_step={} 与 DB node_state 不一致，优先采用 DB 兜底 startStep={}, taskId={}",
                        currentStep, startStep, id);
            }
        } else {
            // 暂停时 currentStep 就是"正在执行的步骤"（步骤N生成到一半，currentStep=N），
            // 继续时应该从该步骤本身续跑，resolveBatchStartIndex 会跳过已完成的项。
            startStep = Math.max(1, currentStep);
        }

        if (startStep > 9) {
            // 已到最后一步，直接标完成
            ComicTask done = new ComicTask();
            done.setId(id);
            done.setStatus(TaskStatus.DONE.getCode());
            done.setEndTime(LocalDateTime.now());
            this.updateById(done);
            writeProgressLog(id, currentStep, null, null, 100, 100, "继续（自动续跑）：已到最后一步，任务完成");
            log.info("resume: 已到最后一步，任务完成 taskId={}", id);
            return;
        }

        // 标记 RUNNING（startStep 即续跑的起始步骤）
        ComicTask running = new ComicTask();
        running.setId(id);
        running.setStatus(TaskStatus.RUNNING.getCode());
        running.setCurrentStep(startStep);
        running.setEndTime(null);
        this.updateById(running);
        writeProgressLog(id, currentStep, null, null, task.getProgress(), task.getProgress(),
                "继续（断点续跑）：从步骤 " + startStep + " 的断点继续，已完成产物不重复生成");

        // 调用 workflow-service 续跑：startStep = startStep, maxStep = 9，
        // 保留已完成产物，断点续跑依赖各步骤 Handler 的 resolveBatchStartIndex 跳过已完成项。
        try {
            String url = restTemplateConfig.getWorkflowServiceUrl()
                    + "/api/workflow/pipeline/resume?startStep=" + startStep + "&maxStep=9";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<java.util.Map<String, Object>> entity = new HttpEntity<>(buildResumeRequest(task, 9), headers);

            log.info("resume：调用 workflow-service 断点续跑 taskId={}, startStep={}", id, startStep);
            ResponseEntity<com.comicdrama.common.result.Result<Void>> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity,
                    new ParameterizedTypeReference<com.comicdrama.common.result.Result<Void>>() {});

            if (!response.getStatusCode().is2xxSuccessful()
                    || response.getBody() == null
                    || (response.getBody().getCode() != 200 && response.getBody().getCode() != 0)) {
                log.error("resume 失败 taskId={}, body={}", id, response.getBody());
                throw new BizException("恢复任务失败，请稍后重试");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("resume 调用 workflow-service 异常 taskId={}", id, e);
            throw new BizException("恢复任务异常：" + e.getMessage());
        }
    }

    @Override
    public void retry(Long id) {
        ComicTask task = this.getById(id);
        if (task == null) {
            throw new BizException(ResultCode.TASK_NOT_FOUND);
        }
        Integer st = task.getStatus();
        boolean isFailed = st != null && st == TaskStatus.FAILED.getCode();
        boolean isQueued = st != null && st == TaskStatus.QUEUE.getCode();
        boolean isPaused = st != null && st == TaskStatus.PAUSED.getCode();
        boolean inMemory = taskQueue.getPosition(id) > 0;

        if (!isFailed && !isQueued && !isPaused) {
            throw new BizException(ResultCode.TASK_STATUS_ILLEGAL, "仅失败/排队/暂停任务可重试");
        }

        // 失败任务正常走重试流程；排队/暂停但内存队列中不存在任务（服务重启导致内存队列丢队），
        // 重新入队但不将 status 重置为 FAILED 语义（不修改失败步骤相关字段）。
        TaskQueueEntry entry = TaskQueueEntry.builder()
                .taskId(id)
                .userId(task.getUserId())
                .priority(100)
                .enqueuedTime(LocalDateTime.now())
                .build();
        if (!inMemory) {
            taskQueue.enqueue(entry);
        }
        int position = taskQueue.getPosition(id);
        ComicTask update = new ComicTask();
        update.setId(id);
        if (isFailed) {
            update.setStatus(TaskStatus.QUEUE.getCode());
            update.setRetryCount((task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1);
        }
        update.setQueuePosition(position);
        this.updateById(update);
        writeProgressLog(id, 0, null, null, 0, 0,
                isFailed
                        ? "任务重试，从失败步骤续跑（步骤 " + task.getFailureStep() + "），排队位置 " + position
                        : inMemory
                                ? "任务重新入队（已在队列中），排队位置 " + position
                                : "任务重新入队（服务重启导致丢队已修复），排队位置 " + position);
    }

    @Override
    public void approve(Long id) {
        ComicTask task = this.getById(id);
        if (task == null) {
            throw new BizException(ResultCode.TASK_NOT_FOUND);
        }
        // 仅审核暂停态（人工审核模式 + PAUSED）可审核通过
        if (task.getExecMode() == null || task.getExecMode() != 1) {
            throw new BizException(ResultCode.TASK_STATUS_ILLEGAL, "仅人工审核模式任务可审核通过");
        }
        if (task.getStatus() == null || task.getStatus() != TaskStatus.PAUSED.getCode()) {
            throw new BizException(ResultCode.TASK_STATUS_ILLEGAL, "仅暂停态任务可审核通过");
        }

        int currentStep = task.getCurrentStep() == null ? 0 : task.getCurrentStep();
        int nextStep = currentStep + 1;
        int maxSteps = 9;

        // 最后一步审核通过 → 直接标记完成
        if (nextStep > maxSteps) {
            ComicTask done = new ComicTask();
            done.setId(id);
            done.setStatus(TaskStatus.DONE.getCode());
            done.setEndTime(LocalDateTime.now());
            this.updateById(done);
            writeProgressLog(id, currentStep, null, null, 100, 100, "最后一步审核通过，任务完成");
            log.info("最后一步审核通过，任务完成 taskId={}", id);
            return;
        }

        // 标记为生成中
        ComicTask running = new ComicTask();
        running.setId(id);
        running.setStatus(TaskStatus.RUNNING.getCode());
        running.setCurrentStep(nextStep);
        running.setEndTime(null);
        this.updateById(running);
        writeProgressLog(id, currentStep, null, null, task.getProgress(), task.getProgress(),
                "步骤 " + currentStep + " 审核通过，继续执行步骤 " + nextStep);

        // 【P5】approve：仅 nextStep 在 node_state 中为 FAILED(9) 时清产物；
        // 非失败（用户手动修改过或从未进入过该步骤）一律保留，避免 resolveBatchStartIndex 将用户修改后的产物误删。
        if (isStepNodeFailed(id, nextStep)) {
            cleanArtifactsIfNeeded(id, nextStep, nextStep);
            writeProgressLog(id, nextStep, null, null, task.getProgress(), task.getProgress(),
                    "步骤 " + nextStep + " 上次执行失败，清理旧产物后重新生成");
        } else {
            log.info("approve: nextStep={} 非 FAILED 状态，跳过 cleanArtifacts 避免误删手动修改产物 taskId={}",
                    nextStep, id);
        }

        // 调用 workflow-service 以 startStep=nextStep, maxStep=nextStep 单步运行（人工审核模式）
        try {
            String url = restTemplateConfig.getWorkflowServiceUrl()
                    + "/api/workflow/pipeline/resume?startStep=" + nextStep + "&maxStep=" + nextStep;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<java.util.Map<String, Object>> entity = new HttpEntity<>(buildResumeRequest(task, nextStep), headers);

            log.info("审核通过：调用 workflow-service 单步续跑 taskId={}, nextStep={}", id, nextStep);
            ResponseEntity<com.comicdrama.common.result.Result<Void>> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity,
                    new ParameterizedTypeReference<com.comicdrama.common.result.Result<Void>>() {});

            if (!response.getStatusCode().is2xxSuccessful()
                    || response.getBody() == null
                    || (response.getBody().getCode() != 200 && response.getBody().getCode() != 0)) {
                log.error("审核通过续跑失败 taskId={}, body={}", id, response.getBody());
                throw new BizException("审核通过续跑失败，请稍后重试");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("审核通过调用 workflow-service 异常 taskId={}", id, e);
            throw new BizException("审核通过续跑异常：" + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void executeNextStep(Long id) {
        ComicTask task = this.getById(id);
        if (task == null) {
            throw new BizException(ResultCode.TASK_NOT_FOUND);
        }
        if (task.getStatus() == null || task.getStatus() != TaskStatus.PAUSED.getCode()) {
            throw new BizException(ResultCode.TASK_STATUS_ILLEGAL, "仅暂停态任务可执行下一步");
        }

        int currentStep = task.getCurrentStep() == null ? 0 : task.getCurrentStep();
        int nextStep = currentStep + 1;
        int maxSteps = 9;

        if (nextStep > maxSteps) {
            // 已到最后一步：直接标记 DONE
            ComicTask done = new ComicTask();
            done.setId(id);
            done.setStatus(TaskStatus.DONE.getCode());
            done.setEndTime(LocalDateTime.now());
            this.updateById(done);
            writeProgressLog(id, currentStep, null, null, 100, 100, "执行下一步：已到最后一步，任务完成");
            log.info("executeNextStep: 已到最后一步，任务完成 taskId={}", id);
            return;
        }

        // 标记为 RUNNING，currentStep = nextStep
        ComicTask running = new ComicTask();
        running.setId(id);
        running.setStatus(TaskStatus.RUNNING.getCode());
        running.setCurrentStep(nextStep);
        running.setEndTime(null);
        this.updateById(running);
        writeProgressLog(id, currentStep, null, null, task.getProgress(), task.getProgress(),
                "执行下一步骤：步骤 " + currentStep + " → 步骤 " + nextStep + "（单步执行，完成后暂停）");

        // 【P5】executeNextStep：仅 nextStep FAILED(9) 时清产物；保留用户手动修改的分镜/视频结果。
        if (isStepNodeFailed(id, nextStep)) {
            cleanArtifactsIfNeeded(id, nextStep, nextStep);
            writeProgressLog(id, nextStep, null, null, task.getProgress(), task.getProgress(),
                    "步骤 " + nextStep + " 上次执行失败，清理旧产物后重新生成");
        } else {
            log.info("executeNextStep: nextStep={} 非 FAILED 状态，跳过 cleanArtifacts taskId={}", nextStep, id);
        }

        // 调用 workflow-service 以 startStep=nextStep, maxStep=nextStep 单步运行
        try {
            String url = restTemplateConfig.getWorkflowServiceUrl()
                    + "/api/workflow/pipeline/resume?startStep=" + nextStep + "&maxStep=" + nextStep;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<java.util.Map<String, Object>> entity = new HttpEntity<>(buildResumeRequest(task, nextStep), headers);

            log.info("executeNextStep：调用 workflow-service 单步续跑 taskId={}, nextStep={}", id, nextStep);
            ResponseEntity<com.comicdrama.common.result.Result<Void>> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity,
                    new ParameterizedTypeReference<com.comicdrama.common.result.Result<Void>>() {});

            if (!response.getStatusCode().is2xxSuccessful()
                    || response.getBody() == null
                    || (response.getBody().getCode() != 200 && response.getBody().getCode() != 0)) {
                log.error("executeNextStep 失败 taskId={}, body={}", id, response.getBody());
                throw new BizException("执行下一步失败，请稍后重试");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("executeNextStep 调用 workflow-service 异常 taskId={}", id, e);
            throw new BizException("执行下一步异常：" + e.getMessage());
        }
    }

    /**
     * 【P4】DB 级 findResumeStep：扫描 task_node_state，返回第一个未完成步骤的 step。
     * node_status 枚举（对齐 NodeStateManager）：5=PAUSED，6=PENDING，7=IN_PROGRESS，8=DONE，9=FAILED。
     * 规则：
     *   - 遇到第一个 FAILED(9) / IN_PROGRESS(7) / PENDING(6) → 直接返回它（必跑）
     *   - 遇到 PAUSED(5) → 返回它（断点续跑起点）
     *   - DONE(8) → 跳过
     *   - 无记录 → 返回 0（调用方退回到 currentStep 逻辑）
     */
    private int findResumeStepFromDb(Long taskId) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT step, node_status FROM task_node_state WHERE task_id=? AND step BETWEEN 1 AND 9 " +
                            "ORDER BY step ASC",
                    taskId);
            if (rows == null || rows.isEmpty()) return 0;
            int firstDone = 0;
            for (Map<String, Object> row : rows) {
                int step = ((Number) row.get("step")).intValue();
                Integer ns = row.get("node_status") == null ? null : ((Number) row.get("node_status")).intValue();
                if (ns == null) continue;
                if (ns == 8) { // DONE
                    firstDone = Math.max(firstDone, step);
                    continue;
                }
                // 非 DONE 状态（PAUSED/IN_PROGRESS/PENDING/FAILED）：此步骤就是续跑起点
                return step;
            }
            // 所有有记录的步骤都 DONE → 返回 firstDone + 1（若 firstDone=9 调用方会走 DONE 分支）
            return firstDone > 0 ? firstDone + 1 : 0;
        } catch (Exception e) {
            log.warn("findResumeStepFromDb 查询失败 taskId={}: {}", taskId, e.getMessage());
            return 0;
        }
    }

    /**
     * 【P5】判断指定步骤在 task_node_state 中是否为 FAILED(9)。
     * approve / executeNextStep 在清理前调用：仅 FAILED 清，其余保留用户手动修改。
     */
    private boolean isStepNodeFailed(Long taskId, int step) {
        try {
            Integer ns = jdbcTemplate.queryForObject(
                    "SELECT node_status FROM task_node_state WHERE task_id=? AND step=? LIMIT 1",
                    Integer.class, taskId, step);
            return ns != null && ns == 9;
        } catch (Exception e) {
            log.warn("isStepNodeFailed 查询失败 taskId={}, step={}: {}", taskId, step, e.getMessage());
            return false;
        }
    }

    /**
     * 清理指定范围步骤的产物（调用 workflow-service clean-artifacts 接口）。
     * 失败只记日志，不抛出（避免阻断正常执行流程，resolveBatchStartIndex 就算有旧数据也只是跳过，不会崩）。
     */
    private void cleanArtifactsIfNeeded(Long taskId, int fromStep, int toStep) {
        try {
            String url = restTemplateConfig.getWorkflowServiceUrl()
                    + "/api/workflow/pipeline/clean-artifacts?fromStep=" + fromStep + "&toStep=" + toStep;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("taskId", taskId);
            HttpEntity<java.util.Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<com.comicdrama.common.result.Result<Void>> resp = restTemplate.exchange(
                    url, HttpMethod.POST, entity,
                    new ParameterizedTypeReference<com.comicdrama.common.result.Result<Void>>() {});
            if (resp.getBody() != null && resp.getBody().getCode() != 0 && resp.getBody().getCode() != 200) {
                log.warn("cleanArtifactsIfNeeded 清理失败 taskId={}, fromStep={}, toStep={}, resp={}",
                        taskId, fromStep, toStep, resp.getBody());
            } else {
                log.info("cleanArtifactsIfNeeded 清理成功 taskId={}, 步骤{}~{}", taskId, fromStep, toStep);
            }
        } catch (Exception e) {
            log.warn("cleanArtifactsIfNeeded 调用异常 taskId={}, fromStep={}, toStep={}", taskId, fromStep, toStep, e);
        }
    }

    /**
     * 构建续跑请求体（复用 PipelineExecuteRequest 结构）。
     */
    private java.util.Map<String, Object> buildResumeRequest(ComicTask task) {
        return buildResumeRequest(task, 9);
    }

    /**
     * 构建续跑请求体，支持指定 maxSteps（单步续跑时传 maxSteps=nextStep）。
     */
    private java.util.Map<String, Object> buildResumeRequest(ComicTask task, int maxSteps) {
        java.util.Map<String, Object> request = new java.util.HashMap<>();
        request.put("taskId", task.getId());
        request.put("userId", task.getUserId());
        request.put("title", task.getTitle());
        request.put("maxSteps", maxSteps);

        java.util.Map<String, Object> dto = new java.util.HashMap<>();
        dto.put("title", task.getTitle());
        dto.put("storyRequirement", task.getStoryRequirement());
        dto.put("duration", task.getDuration());
        dto.put("aspectRatio", task.getAspectRatio());
        dto.put("resolution", task.getResolution());
        dto.put("voiceEnabled", task.getVoiceEnabled());
        dto.put("execMode", task.getExecMode());
        dto.put("artStyle", task.getArtStyle());
        dto.put("visualStyle", task.getVisualStyle());
        dto.put("remark", task.getRemark());
        request.put("taskCreateDTO", dto);
        return request;
    }

    /** 生成业务任务编号：TASK + yyyyMMddHHmmss + 4位随机 */
    private String generateTaskNo() {
        return "TASK" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + RandomUtil.randomNumbers(4);
    }

    /** 写一条进度日志 */
    public void writeProgressLog(Long taskId, Integer step, String nodeType, String nodeKey,
                                 Integer progress, Integer totalProgress, String message) {
        TaskProgressLog log = new TaskProgressLog();
        log.setTaskId(taskId);
        log.setStep(step);
        log.setNodeType(nodeType);
        log.setNodeKey(nodeKey);
        log.setProgress(progress == null ? 0 : progress);
        log.setTotalProgress(totalProgress == null ? 0 : totalProgress);
        log.setMessage(message);
        log.setIsPushed(0);
        taskProgressLogMapper.insert(log);
    }

    @Override
    public String getOrBuildFinalWorkManifest(Long id) {
        ComicTask task = this.getById(id);
        if (task == null) {
            throw new BizException("任务不存在");
        }
        // 1) 优先读已持久化的 manifest
        if (StringUtils.hasText(task.getFinalWorkManifest())) {
            return task.getFinalWorkManifest();
        }
        // 2) 否则从 scene_video 表动态构建（兼容历史任务/功能发布前已完成的任务）
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, scene_group_id, video_url, thumbnail_url, duration, resolution, create_time " +
                        "FROM scene_video WHERE task_id = ? ORDER BY scene_group_id, id",
                id);
        int totalDuration = 0;
        List<Map<String, Object>> entries = new ArrayList<>();
        int seq = 0;
        for (Map<String, Object> row : rows) {
            seq++;
            Object durationObj = row.get("duration");
            Integer duration = durationObj instanceof Number ? ((Number) durationObj).intValue() : 0;
            totalDuration += duration;
            Long sceneGroupId = row.get("scene_group_id") != null
                    ? ((Number) row.get("scene_group_id")).longValue() : null;
            Map<String, Object> entry = new java.util.LinkedHashMap<>();
            entry.put("orderIndex", seq);
            entry.put("filename", String.format("%03d_scene%s.mp4",
                    seq, sceneGroupId != null ? sceneGroupId.toString() : String.valueOf(seq)));
            entry.put("sceneGroupId", sceneGroupId);
            entry.put("storyboardSeqRange", (sceneGroupId != null) ? ("scene_" + sceneGroupId) : ("片段_" + seq));
            entry.put("duration", duration);
            entry.put("originalUrl", row.get("video_url"));
            entry.put("coverUrl", row.get("thumbnail_url"));
            entries.add(entry);
        }
        Map<String, Object> manifest = new java.util.LinkedHashMap<>();
        manifest.put("taskId", task.getId());
        manifest.put("taskNo", task.getTaskNo());
        manifest.put("title", task.getTitle());
        manifest.put("totalDuration", totalDuration);
        manifest.put("segmentCount", entries.size());
        manifest.put("resolution", task.getResolution());
        manifest.put("aspectRatio", task.getAspectRatio());
        manifest.put("createdAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        manifest.put("videos", entries);
        try {
            String json = objectMapper.writeValueAsString(manifest);
            // 2.1) 回写 DB，避免下次再构建（也顺便让 VideoMergeStepHandler 生成的最终 manifest 保持一致语义）
            try {
                jdbcTemplate.update(
                        "UPDATE comic_task SET final_work_manifest = ? WHERE id = ?",
                        json, id);
            } catch (Exception e) {
                log.warn("回写 final_work_manifest 失败 taskId={}", id, e);
            }
            return json;
        } catch (Exception e) {
            throw new BizException("构建播放清单失败：" + e.getMessage());
        }
    }
}
