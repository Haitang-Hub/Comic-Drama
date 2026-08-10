package com.comicdrama.task.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.dto.TaskCreateDTO;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.task.entity.ComicTask;
import com.comicdrama.task.vo.TaskDetailVO;

/**
 * 漫剧任务服务
 */
public interface TaskService extends IService<ComicTask> {

    /** 创建任务：生成 task_no、入队、写进度日志，返回新建任务 */
    ComicTask createTask(TaskCreateDTO dto);

    /** 分页（默认仅当前用户任务；admin 可查全部） */
    PageResult<ComicTask> page(PageQuery query, String keyword, Integer status, boolean queryAll);

    /** 获取任务详情（包含所有产物数据） */
    TaskDetailVO getDetail(Long id);

    void pause(Long id);

    /**
     * 暂停并可选回退当前步骤（删除当前步骤半成品产物）。
     *
     * @param id                     任务ID
     * @param rollbackCurrentStep   true=清除当前步骤的产物并将 currentStep 回退至上一步完成的阶段；
     *                               false=保留当前步骤的全部产物（完成此阶段的语义）
     */
    void pause(Long id, boolean rollbackCurrentStep);

    /**
     * 暂停（完整三语义控制）。
     *
     * @param id                      任务ID
     * @param rollbackCurrentStep     true=立即清除当前步骤产物+回退currentStep；
     *                                false=保留产物，是否立即停止由 stopAfterCurrentStep 决定
     * @param stopAfterCurrentStep    当 rollbackCurrentStep=false 时生效：
     *                                true=「完成此阶段」：等当前正在执行的步骤执行完毕后，在下一步骤边界处暂停；
     *                                false=立即标记任务为 PAUSED（保留产物但不强制等当前步跑完）
     */
    void pause(Long id, boolean rollbackCurrentStep, boolean stopAfterCurrentStep);

    /**
     * 恢复（自动模式续跑全部语义）：
     * 从 currentStep+1 开始执行后续的全部步骤直到结束（步骤9），不再主动暂停。
     * 不清理任何产物（续跑语义）。
     */
    void resume(Long id);

    void retry(Long id);

    /** 审核通过：人工审核模式下，从当前步骤的下一步继续执行（单步） */
    void approve(Long id);

    /**
     * 执行下一步骤（单步语义）。
     * 从 currentStep+1 开始执行单一的下一个步骤，完成后暂停等待用户下一次确认。
     * 执行前会清理 [nextStep, nextStep] 的旧产物（保证重新做而不是跳过时出现"成功0/N"）。
     */
    void executeNextStep(Long id);
}
