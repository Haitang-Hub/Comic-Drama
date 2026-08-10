package com.comicdrama.task.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.comicdrama.common.enums.TaskStatus;
import com.comicdrama.task.entity.ComicTask;
import com.comicdrama.task.entity.TaskProgressLog;
import com.comicdrama.task.mapper.ComicTaskMapper;
import com.comicdrama.task.mapper.TaskProgressLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 任务看门狗调度器。
 * <p>
 * 定期检测长时间无进展的 RUNNING 任务，自动将其标记为 FAILED，
 * 防止任务因 AI 调用卡死、网络中断等原因永远停留在「生成中」状态。
 * </p>
 *
 * <h3>判定规则</h3>
 * <ul>
 *   <li>任务状态为 RUNNING (1)</li>
 *   <li>start_time 距今超过超时阈值（默认 30 分钟）</li>
 *   <li>且最近一条 task_progress_log 距今也超过阈值（无任何进度更新）</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskWatchdogScheduler {

    private final ComicTaskMapper comicTaskMapper;
    private final TaskProgressLogMapper taskProgressLogMapper;

    /** 任务超时阈值（分钟），默认 30 分钟 */
    @Value("${task.watchdog.timeout-minutes:30}")
    private int timeoutMinutes;

    /** 看门狗轮询间隔（毫秒），默认 2 分钟 */
    @Value("${task.watchdog.check-interval-ms:120000}")
    private long checkIntervalMs;

    /**
     * 定期检测卡死的 RUNNING 任务并标记为失败。
     */
    @Scheduled(fixedDelayString = "${task.watchdog.check-interval-ms:120000}")
    public void checkStaleTasks() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.minus(timeoutMinutes, ChronoUnit.MINUTES);

        LambdaQueryWrapper<ComicTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ComicTask::getStatus, TaskStatus.RUNNING.getCode())
               .lt(ComicTask::getStartTime, threshold)
               .orderByDesc(ComicTask::getStartTime)
               .last("LIMIT 20");

        List<ComicTask> staleTasks = comicTaskMapper.selectList(wrapper);

        if (staleTasks.isEmpty()) {
            return;
        }

        log.warn("【看门狗】发现 {} 个超时未更新的任务（阈值 {} 分钟）", staleTasks.size(), timeoutMinutes);

        for (ComicTask task : staleTasks) {
            try {
                handleStaleTask(task, now);
            } catch (Exception e) {
                log.error("【看门狗】处理超时任务失败 taskId={}", task.getId(), e);
            }
        }
    }

    private void handleStaleTask(ComicTask task, LocalDateTime now) {
        Long taskId = task.getId();

        // 检查最近一次进度日志时间（如果最近有进度更新，说明任务仍在执行中，只是 AI 调用较慢）
        LambdaQueryWrapper<TaskProgressLog> logWrapper = new LambdaQueryWrapper<>();
        logWrapper.eq(TaskProgressLog::getTaskId, taskId)
                  .orderByDesc(TaskProgressLog::getCreateTime)
                  .last("LIMIT 1");
        TaskProgressLog latestLog = taskProgressLogMapper.selectOne(logWrapper);

        if (latestLog != null && latestLog.getCreateTime() != null) {
            long minutesSinceLastProgress = ChronoUnit.MINUTES.between(latestLog.getCreateTime(), now);
            // 如果最近 10 分钟内有进度更新，认为任务仍在活跃执行，暂不标记超时
            if (minutesSinceLastProgress < 10) {
                log.info("【看门狗】任务 {} 最近 {} 分钟内有进度更新，跳过超时标记", taskId, minutesSinceLastProgress);
                return;
            }
        }

        // 标记任务为失败：超时未响应
        ComicTask failed = new ComicTask();
        failed.setId(taskId);
        failed.setStatus(TaskStatus.FAILED.getCode());
        failed.setFailureStep(task.getCurrentStep() != null ? task.getCurrentStep() : 1);
        failed.setFailureReason("任务执行超时（超过 " + timeoutMinutes + " 分钟无进展），可能原因：AI 模型响应超时、网络中断或服务异常");
        failed.setFailureDetail("任务开始时间：" + task.getStartTime()
                + "，超时检测时间：" + now
                + "，最近进度日志时间：" + (latestLog != null ? latestLog.getCreateTime() : "无")
                + "，当前步骤：" + (task.getCurrentStep() != null ? task.getCurrentStep() : "未知"));
        failed.setEndTime(now);
        comicTaskMapper.updateById(failed);

        log.warn("【看门狗】任务 {} 已标记为失败（超时），步骤：{}", taskId, task.getCurrentStep());

        // 写入进度日志记录
        TaskProgressLog timeoutLog = new TaskProgressLog();
        timeoutLog.setTaskId(taskId);
        timeoutLog.setStep(task.getCurrentStep() != null ? task.getCurrentStep() : 0);
        timeoutLog.setProgress(0);
        timeoutLog.setTotalProgress(task.getProgress() != null ? task.getProgress() : 0);
        timeoutLog.setMessage("⚠️ 任务超时：超过 " + timeoutMinutes + " 分钟无进展，已自动标记为失败。可点击「重新生成」重试");
        timeoutLog.setIsPushed(0);
        taskProgressLogMapper.insert(timeoutLog);
    }
}