package com.comicdrama.task.schedule;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.comicdrama.common.enums.TaskStatus;
import com.comicdrama.common.dto.TaskCreateDTO;
import com.comicdrama.common.result.Result;
import com.comicdrama.common.service.WorkflowTaskInfo;
import com.comicdrama.common.service.TaskInfoProvider;
import com.comicdrama.common.service.TaskStateManager;
import com.comicdrama.common.service.ProgressReporter;
import com.comicdrama.common.service.FailureReporter;
import com.comicdrama.common.service.WorkCreator;
import com.comicdrama.task.config.RestTemplateConfig;
import com.comicdrama.task.entity.ComicTask;
import com.comicdrama.task.entity.TaskFailureLog;
import com.comicdrama.task.entity.TaskProgressLog;
import com.comicdrama.task.entity.TaskQueue;
import com.comicdrama.task.mapper.ComicTaskMapper;
import com.comicdrama.task.mapper.TaskFailureLogMapper;
import com.comicdrama.task.mapper.TaskProgressLogMapper;
import com.comicdrama.task.mapper.TaskQueueMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 任务流水线运行器（Phase-4：通过 HTTP 调用 workflow-service 执行流水线）。
 *
 * <p>协调 {@link TaskQueueScheduler} 和 workflow-service 的 {@link PipelineController}，
 * 实现任务生命周期管理和流水线执行。</p>
 *
 * <h3>职责</h3>
 * <ol>
 *   <li>实现 {@link TaskInfoProvider}：从数据库获取任务信息</li>
 *   <li>实现 {@link TaskStateManager}：管理任务状态流转（本地兜底）</li>
 *   <li>实现 {@link ProgressReporter}：记录进度日志（本地兜底）</li>
 *   <li>实现 {@link FailureReporter}：记录失败日志（本地兜底）</li>
 *   <li>实现 {@link WorkCreator}：创建 ComicWork 记录</li>
 *   <li>提供 {@link #runTask(Long)} 方法供调度器调用</li>
 * </ol>
 */
@Slf4j
@Component
public class TaskPipelineRunner implements
        TaskInfoProvider,
        TaskStateManager,
        ProgressReporter,
        FailureReporter,
        WorkCreator {

    private final ComicTaskMapper comicTaskMapper;
    private final TaskQueueMapper taskQueueMapper;
    private final TaskProgressLogMapper taskProgressLogMapper;
    private final TaskFailureLogMapper taskFailureLogMapper;
    private final RestTemplate restTemplate;
    private final RestTemplateConfig restTemplateConfig;

    public TaskPipelineRunner(ComicTaskMapper comicTaskMapper,
                              TaskQueueMapper taskQueueMapper,
                              TaskProgressLogMapper taskProgressLogMapper,
                              TaskFailureLogMapper taskFailureLogMapper,
                              RestTemplate restTemplate,
                              RestTemplateConfig restTemplateConfig) {
        this.comicTaskMapper = comicTaskMapper;
        this.taskQueueMapper = taskQueueMapper;
        this.taskProgressLogMapper = taskProgressLogMapper;
        this.taskFailureLogMapper = taskFailureLogMapper;
        this.restTemplate = restTemplate;
        this.restTemplateConfig = restTemplateConfig;
    }

    /**
     * 执行任务流水线（供 TaskQueueScheduler 调用）。
     * 通过 HTTP 调用 workflow-service 的 PipelineController 执行 AI 流水线。
     *
     * <p>如果任务存在 failureStep（即失败重试），则从失败步骤续跑（调用 /resume），
     * 否则从头执行（调用 /execute）。</p>
     *
     * @param taskId 任务 ID
     */
    public void runTask(Long taskId) {
        ComicTask task = comicTaskMapper.selectById(taskId);
        if (task == null) {
            log.error("任务不存在 taskId={}", taskId);
            return;
        }

        Integer failureStep = task.getFailureStep();
        Integer currentStep = task.getCurrentStep();
        Integer status = task.getStatus();
        Integer execMode = task.getExecMode();

        // 判断起始步骤：
        // 1. 暂停恢复：PAUSED 状态，优先使用 currentStep（已完成的步骤），从 currentStep 继续
        // 2. 失败重试：FAILED 状态，从 failureStep 续跑
        // 3. 普通恢复：QUEUE 状态有 currentStep>0 说明之前执行到某步（从队列消费时）
        // 4. 首次执行：从头开始
        Integer startStep = null;
        boolean isResume = false;

        boolean isPaused = status != null && status == TaskStatus.PAUSED.getCode();
        boolean isFailed = status != null && status == TaskStatus.FAILED.getCode();
        boolean isManualReview = execMode != null && execMode == 1;

        if (isFailed && failureStep != null && failureStep > 0) {
            // 失败重试：从失败步骤续跑
            startStep = failureStep;
            isResume = true;
            log.info("任务从失败步骤续跑 taskId={}, failureStep={}", taskId, failureStep);
        } else if (currentStep != null && currentStep > 0) {
            // 暂停恢复 / 普通恢复：从 currentStep 继续
            // 注意：markAsPaused 设置的 current_step 是"已完成的步骤"，需要从该步骤重新执行
            //   - 人工审核模式：步骤N完成后暂停，current_step=N，审核通过后 approve() 会直接调用 workflow
            //     所以走到这里的 resume() 是"继续"按钮（非审核态暂停），从 currentStep 重新执行
            //   - 非审核模式暂停：current_step 表示暂停时的步骤，从该步骤继续
            startStep = currentStep;
            isResume = true;
            log.info("任务从当前步骤续跑 taskId={}, currentStep={}, execMode={}", taskId, currentStep, execMode);
        } else {
            log.info("任务从头执行 taskId={}, title={}", taskId, task.getTitle());
        }

        Map<String, Object> request = buildPipelineRequest(task);

        String url;
        if (isResume) {
            url = restTemplateConfig.getWorkflowServiceUrl()
                    + "/api/workflow/pipeline/resume?startStep=" + startStep;
        } else {
            url = restTemplateConfig.getWorkflowServiceUrl() + "/api/workflow/pipeline/execute";
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        try {
            log.info("调用 workflow-service: {}", url);
            ResponseEntity<Result<Void>> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity,
                    new ParameterizedTypeReference<Result<Void>>() {});

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Result<Void> body = response.getBody();
                if (body.getCode() == 200 || body.getCode() == 0) {
                    log.info("流水线执行成功 taskId={}", taskId);
                } else {
                    log.error("流水线执行失败 taskId={}, msg={}", taskId, body.getMsg());
                }
            } else {
                log.error("workflow-service 返回异常 taskId={}, status={}", taskId, response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("调用 workflow-service 异常 taskId={}", taskId, e);
            markAsFailed(taskId, startStep != null ? startStep : 1,
                    "workflow-service 调用失败", e.getMessage(), LocalDateTime.now());
        }
    }

    /**
     * 构建流水线执行请求体。
     */
    private Map<String, Object> buildPipelineRequest(ComicTask task) {
        Map<String, Object> request = new HashMap<>();
        request.put("taskId", task.getId());
        request.put("userId", task.getUserId());
        request.put("title", task.getTitle());
        request.put("maxSteps", 9);

        Map<String, Object> dto = new HashMap<>();
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

    // ==================== TaskInfoProvider 实现 ====================

    @Override
    public WorkflowTaskInfo getTaskInfo(Long taskId) {
        ComicTask task = comicTaskMapper.selectById(taskId);
        if (task == null) {
            log.warn("任务不存在 taskId={}", taskId);
            return null;
        }

        TaskCreateDTO dto = buildTaskCreateDTO(task);

        return new WorkflowTaskInfo(
                task.getId(),
                task.getTaskNo(),
                task.getUserId(),
                task.getTitle(),
                dto
        );
    }

    @Override
    public void registerTask(Long taskId, Long userId, String title, TaskCreateDTO dto) {
        // TaskPipelineRunner 是数据库侧的 TaskInfoProvider 实现，
        // registerTask 在此处为 no-op（DTO 参数已通过 buildPipelineRequest 构建并传递给 workflow-service）。
        // workflow-service 的 DefaultTaskInfoProvider 会自行维护内存缓存。
        log.debug("registerTask (no-op in TaskPipelineRunner) taskId={}", taskId);
    }

    /**
     * 从 ComicTask 实体构建 TaskCreateDTO。
     */
    private TaskCreateDTO buildTaskCreateDTO(ComicTask task) {
        TaskCreateDTO dto = new TaskCreateDTO();
        dto.setTitle(task.getTitle());
        dto.setStoryRequirement(task.getStoryRequirement());
        dto.setDuration(task.getDuration());
        dto.setAspectRatio(task.getAspectRatio());
        dto.setResolution(task.getResolution());
        dto.setVoiceEnabled(task.getVoiceEnabled());
        dto.setExecMode(task.getExecMode());
        dto.setArtStyle(task.getArtStyle());
        dto.setVisualStyle(task.getVisualStyle());
        dto.setRemark(task.getRemark());
        return dto;
    }

    // ==================== TaskStateManager 实现（本地兜底） ====================

    @Override
    public void markAsRunning(Long taskId, int currentStep, LocalDateTime startTime) {
        ComicTask running = new ComicTask();
        running.setId(taskId);
        running.setStatus(TaskStatus.RUNNING.getCode());
        running.setCurrentStep(currentStep);
        running.setStartTime(startTime);
        comicTaskMapper.updateById(running);

        TaskQueue qRunning = new TaskQueue();
        qRunning.setQueueStatus(1);
        qRunning.setStartedTime(startTime);
        taskQueueMapper.update(qRunning,
                new LambdaQueryWrapper<TaskQueue>().eq(TaskQueue::getTaskId, taskId));

        log.info("任务标记为生成中 taskId={}, step={}", taskId, currentStep);
    }

    @Override
    public void updateStepProgress(Long taskId, int currentStep, int progress, int totalProgress) {
        ComicTask update = new ComicTask();
        update.setId(taskId);
        update.setCurrentStep(currentStep);
        update.setProgress(totalProgress);
        comicTaskMapper.updateById(update);
    }

    @Override
    public void markAsDone(Long taskId, int progress, int totalConsumeTime,
                           String coverUrl, String finalVideoUrl, LocalDateTime endTime) {
        markAsDone(taskId, progress, totalConsumeTime, coverUrl, finalVideoUrl, endTime, 9);
    }

    @Override
    public void markAsDone(Long taskId, int progress, int totalConsumeTime,
                           String coverUrl, String finalVideoUrl, LocalDateTime endTime,
                           int completedStep) {
        ComicTask done = new ComicTask();
        done.setId(taskId);
        done.setStatus(TaskStatus.DONE.getCode());
        done.setProgress(progress);
        done.setCurrentStep(completedStep);
        done.setEndTime(endTime);
        done.setTotalConsumeTime(totalConsumeTime);
        done.setCoverUrl(coverUrl);
        done.setFinalVideoUrl(finalVideoUrl);
        comicTaskMapper.updateById(done);

        TaskQueue qDone = new TaskQueue();
        qDone.setQueueStatus(2);
        qDone.setFinishedTime(endTime);
        taskQueueMapper.update(qDone,
                new LambdaQueryWrapper<TaskQueue>().eq(TaskQueue::getTaskId, taskId));

        log.info("任务标记为已完成 taskId={}, progress={}, consumeTime={}s, completedStep={}",
                taskId, progress, totalConsumeTime, completedStep);
    }

    @Override
    public void markAsFailed(Long taskId, int failureStep, String failureReason,
                             String failureDetail, LocalDateTime endTime) {
        ComicTask failed = new ComicTask();
        failed.setId(taskId);
        failed.setStatus(TaskStatus.FAILED.getCode());
        failed.setFailureStep(failureStep);
        failed.setFailureReason(failureReason);
        failed.setFailureDetail(failureDetail);
        failed.setEndTime(endTime);
        comicTaskMapper.updateById(failed);

        TaskQueue qFailed = new TaskQueue();
        qFailed.setQueueStatus(3);
        qFailed.setFinishedTime(endTime);
        taskQueueMapper.update(qFailed,
                new LambdaQueryWrapper<TaskQueue>().eq(TaskQueue::getTaskId, taskId));

        log.error("任务标记为失败 taskId={}, failureStep={}, reason={}",
                taskId, failureStep, failureReason);
    }

    @Override
    public void markAsPaused(Long taskId, int pausedStep, LocalDateTime pauseTime) {
        ComicTask paused = new ComicTask();
        paused.setId(taskId);
        paused.setStatus(TaskStatus.PAUSED.getCode());
        paused.setCurrentStep(pausedStep);
        paused.setEndTime(pauseTime);
        comicTaskMapper.updateById(paused);

        TaskQueue qPaused = new TaskQueue();
        qPaused.setQueueStatus(3);
        qPaused.setFinishedTime(pauseTime);
        taskQueueMapper.update(qPaused,
                new LambdaQueryWrapper<TaskQueue>().eq(TaskQueue::getTaskId, taskId));

        log.info("任务标记为暂停 taskId={}, pausedStep={}", taskId, pausedStep);
    }

    // ==================== ProgressReporter 实现 ====================

    @Override
    public void reportProgress(Long taskId, Integer step, Integer progress,
                               Integer totalProgress, String message) {
        TaskProgressLog logEntity = new TaskProgressLog();
        logEntity.setTaskId(taskId);
        logEntity.setStep(step);
        logEntity.setProgress(progress);
        logEntity.setTotalProgress(totalProgress);
        logEntity.setMessage(message);
        logEntity.setIsPushed(0);
        taskProgressLogMapper.insert(logEntity);

        log.debug("进度记录 taskId={}, step={}, progress={}, totalProgress={}, msg={}",
                taskId, step, progress, totalProgress, message);
    }

    // ==================== FailureReporter 实现 ====================

    @Override
    public void reportFailure(Long taskId, Integer step, String stepCode,
                              String errorMsg, Throwable throwable) {
        TaskFailureLog failureLog = new TaskFailureLog();
        failureLog.setTaskId(taskId);
        failureLog.setStep(step);
        failureLog.setNodeType(stepCode);
        failureLog.setNodeKey(stepCode + "_fail");
        failureLog.setErrorMessage(errorMsg);
        failureLog.setErrorStack(getStackTrace(throwable));
        failureLog.setResolved(0);
        taskFailureLogMapper.insert(failureLog);

        log.error("失败记录 taskId={}, step={}, stepCode={}, error={}",
                taskId, step, stepCode, errorMsg);
    }

    // ==================== WorkCreator 实现 ====================

    @Override
    public Long createComicWork(Long taskId, Long userId, String title) {
        log.info("创建 ComicWork 记录 taskId={}, userId={}, title={}", taskId, userId, title);

        String workNo = "WORK" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + RandomUtil.randomNumbers(4);

        log.info("ComicWork 占位创建 workNo={}, taskId={}", workNo, taskId);
        return 0L;
    }

    // ==================== 工具方法 ====================

    private String getStackTrace(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(throwable.getClass().getName()).append(": ").append(throwable.getMessage()).append("\n");
        for (StackTraceElement element : throwable.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }
        return sb.toString();
    }
}