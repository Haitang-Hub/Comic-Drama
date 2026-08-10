<template>
  <div class="task-create">
    <div class="form-card sketch-card">
      <div class="form-head">
        <h2>创建漫剧任务</h2>
        <p>填写故事需求，AI 将自动完成 9 步工作流生成</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
        class="form-body"
      >
        <el-form-item label="任务标题" prop="title">
          <el-input v-model="form.title" placeholder="选填，如「赛博朋克爱情故事」" maxlength="50" show-word-limit />
        </el-form-item>

        <el-form-item label="故事需求" prop="storyRequirement">
          <el-input
            v-model="form.storyRequirement"
            type="textarea"
            :rows="5"
            placeholder="用自然语言描述你的故事，如：一个机器人爱上了花店女孩，两人在雨天相遇……"
            maxlength="2000"
            show-word-limit
          />
        </el-form-item>

        <div class="form-row">
          <el-form-item label="剧情时长（秒）" prop="duration">
            <el-input-number v-model="form.duration" :min="5" :max="300" :step="5" controls-position="right" />
          </el-form-item>

          <el-form-item label="画幅比例" prop="aspectRatio">
            <el-select v-model="form.aspectRatio" placeholder="选择画幅">
              <el-option v-for="r in ASPECT_RATIOS" :key="r.value" :label="r.label" :value="r.value" />
            </el-select>
          </el-form-item>

          <el-form-item label="分辨率" prop="resolution">
            <el-select v-model="form.resolution" placeholder="选择分辨率">
              <el-option v-for="r in RESOLUTIONS" :key="r" :label="r" :value="r" />
            </el-select>
          </el-form-item>

          <el-form-item>
            <template #label>
              <span>AI 配音 <span class="step-badge">步骤7</span></span>
            </template>
            <el-switch v-model="voiceEnabled" active-text="开启" inactive-text="关闭" />
            <div class="field-tip">{{ voiceEnabled ? '开启配音合成，为每个分镜生成角色配音' : '关闭后跳过「配音合成」步骤' }}</div>
          </el-form-item>
        </div>

        <!-- 漫剧画风+风格 -->
        <div class="style-combo">
          <div class="style-combo-title">
            <span class="style-combo-label">漫剧画风 + 风格</span>
            <span class="style-combo-formula">画风 + 风格 = 最终视觉定位</span>
          </div>
          <div class="style-combo-row">
            <el-form-item label="画风（基础技法）" prop="artStyle">
              <el-select v-model="form.artStyle" placeholder="选择画风" clearable @change="onArtStyleChange">
                <el-option v-for="a in ART_STYLES" :key="a.value" :label="a.label" :value="a.value">
                  <span class="opt-label">{{ a.label }}</span>
                  <span class="opt-desc">{{ a.desc }}</span>
                </el-option>
                <el-option value="__custom__" label="自定义">
                  <span class="opt-label">✏️ 自定义</span>
                  <span class="opt-desc">输入自定义画风描述</span>
                </el-option>
              </el-select>
              <el-input
                v-if="artStyleCustom"
                v-model="artStyleCustomText"
                placeholder="输入自定义画风，如：版画、剪纸、工笔……"
                maxlength="100"
                show-word-limit
                class="custom-input"
                @input="onArtStyleCustomInput"
              />
            </el-form-item>
            <el-form-item label="风格（美学调性）" prop="visualStyle">
              <el-select v-model="form.visualStyle" placeholder="选择风格" clearable @change="onVisualStyleChange">
                <el-option v-for="s in VISUAL_STYLES" :key="s.value" :label="s.label" :value="s.value">
                  <span class="opt-label">{{ s.label }}</span>
                  <span class="opt-desc">{{ s.desc }}</span>
                </el-option>
                <el-option value="__custom__" label="自定义">
                  <span class="opt-label">✏️ 自定义</span>
                  <span class="opt-desc">输入自定义风格描述</span>
                </el-option>
              </el-select>
              <el-input
                v-if="visualStyleCustom"
                v-model="visualStyleCustomText"
                placeholder="输入自定义风格，如：蒸汽朋克、极简主义……"
                maxlength="100"
                show-word-limit
                class="custom-input"
                @input="onVisualStyleCustomInput"
              />
            </el-form-item>
          </div>
          <div v-if="visualPositioning" class="style-combo-preview">
            <span class="preview-tag">最终视觉定位</span>
            <span class="preview-text">{{ visualPositioning }}</span>
          </div>
        </div>

        <el-form-item label="执行模式">
          <div class="exec-mode-row">
            <el-radio-group v-model="form.execMode">
              <el-radio :value="0">全自动</el-radio>
              <el-radio :value="1">人工审核</el-radio>
            </el-radio-group>
            <span class="field-tip inline-tip">
              {{ form.execMode === 1
                ? '每个步骤完成后自动暂停，审核产物通过后再执行下一步'
                : '所有步骤连续执行，无需人工干预' }}
            </span>
          </div>
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填" maxlength="500" />
        </el-form-item>

        <div class="form-actions">
          <button class="sketch-btn sketch-btn--ghost" type="button" @click="goBack">取消</button>
          <button class="sketch-btn submit-btn" type="button" :disabled="submitting" @click="handleSubmit">
            <el-icon v-if="submitting" class="is-loading"><Loading /></el-icon>
            <span>{{ submitting ? '提交中…' : '提交任务' }}</span>
          </button>
        </div>
      </el-form>
    </div>

    <!-- 流水线预览 -->
    <div class="preview-card sketch-card">
      <h3>提交后将自动执行</h3>
      <div class="preview-decor">
        <StickyNote :size="80" :rotate="-8" color="primary" />
        <Marker :size="100" :rotate="-20" color="accent" />
      </div>
      <div class="preview-steps">
        <div v-for="(name, idx) in stepList" :key="idx" class="preview-step"
             :class="{ 'is-skipped': isStepSkipped(idx + 1) }">
          <div class="preview-num" :class="{ 'is-skipped': isStepSkipped(idx + 1) }">{{ idx + 1 }}</div>
          <span class="step-name">{{ name }}</span>
          <span v-if="isStepSkipped(idx + 1)" class="skip-tag">已跳过</span>
        </div>
      </div>
      <div class="preview-tip">
        <el-icon><InfoFilled /></el-icon>
        <span v-if="!voiceEnabled">配音已关闭，流水线跳过步骤7「配音合成」，后续步骤不受影响。</span>
              <span v-else>开启配音后，9 步流水线将完整执行，包括角色配音合成。</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Loading, InfoFilled } from '@element-plus/icons-vue'
import { createTask, type TaskCreateDTO } from '@/api/task'
import { ASPECT_RATIOS, RESOLUTIONS, STEP_NAMES, ART_STYLES, VISUAL_STYLES, getVisualPositioning } from '@/constants/task'
import { StickyNote, Marker } from '@/components/illustrations'

const router = useRouter()
const formRef = ref<FormInstance>()
const submitting = ref(false)

const stepList = Object.values(STEP_NAMES)

const form = reactive<TaskCreateDTO>({
  title: '',
  storyRequirement: '',
  duration: 60,
  aspectRatio: '16:9',
  resolution: '1080p',
  voiceEnabled: 0,
  execMode: 0,
  artStyle: '',
  visualStyle: '',
  remark: ''
})

// 自定义画风/风格交互
const artStyleCustom = ref(false)
const artStyleCustomText = ref('')
const visualStyleCustom = ref(false)
const visualStyleCustomText = ref('')

function onArtStyleChange(val: string) {
  if (val === '__custom__') {
    artStyleCustom.value = true
    form.artStyle = ''
  } else {
    artStyleCustom.value = false
    artStyleCustomText.value = ''
  }
}

function onArtStyleCustomInput(val: string) {
  form.artStyle = val
}

function onVisualStyleChange(val: string) {
  if (val === '__custom__') {
    visualStyleCustom.value = true
    form.visualStyle = ''
  } else {
    visualStyleCustom.value = false
    visualStyleCustomText.value = ''
  }
}

function onVisualStyleCustomInput(val: string) {
  form.visualStyle = val
}

const visualPositioning = computed(() => getVisualPositioning(form.artStyle, form.visualStyle))

const voiceEnabled = computed({
  get: () => form.voiceEnabled === 1,
  set: (v: boolean) => {
    form.voiceEnabled = v ? 1 : 0
  }
})

function isStepSkipped(stepOrder: number): boolean {
  // 步骤7（配音合成）在 voiceEnabled=0 时跳过
  if (stepOrder === 7) {
    return form.voiceEnabled === 0
  }
  return false
}

const rules: FormRules = {
  storyRequirement: [
    { required: true, message: '请输入故事需求', trigger: 'blur' },
    { min: 5, message: '故事需求至少 5 个字', trigger: 'blur' }
  ],
  duration: [{ required: true, message: '请输入剧情时长', trigger: 'blur' }],
  aspectRatio: [{ required: true, message: '请选择画幅', trigger: 'change' }],
  resolution: [{ required: true, message: '请选择分辨率', trigger: 'change' }],
  artStyle: [{ required: true, message: '请选择画风', trigger: 'change' }],
  visualStyle: [{ required: true, message: '请选择风格', trigger: 'change' }]
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) {
      ElMessage.warning('请完善表单必填项')
      return
    }
    submitting.value = true
    try {
      const task = await createTask(form)
      ElMessage.success(`任务已创建：${task.taskNo}，已进入队列`)
      router.replace('/task')
    } catch (e: any) {
      const msg = e?.message || ''
      if (msg.includes('AI模型配置缺失')) {
        ElMessageBox.confirm(
          `${msg}\n\n是否前往系统设置 → 模型配置页面进行配置？`,
          '模型配置缺失',
          { confirmButtonText: '前往配置', cancelButtonText: '稍后配置', type: 'warning' }
        ).then(() => {
          router.push('/admin')
        }).catch(() => {})
      } else {
        ElMessage.error(msg || '创建任务失败')
      }
    } finally {
      submitting.value = false
    }
  })
}

function goBack() {
  router.back()
}
</script>

<style scoped>
.task-create {
  display: grid;
  grid-template-columns: 1.6fr 1fr;
  gap: 20px;
  align-items: start;
}

.form-card {
  padding: 28px 30px;
}
.form-head {
  margin-bottom: 24px;
}
.form-head h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 800;
  color: var(--cd-text);
}
.form-head p {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--cd-text-secondary);
}

.form-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.style-combo {
  margin: 16px 0;
  padding: 16px 20px;
  background-color: var(--cd-bg-soft);
  border: 1.5px dashed var(--cd-border);
  border-radius: 8px;
}
.style-combo-title {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.style-combo-label {
  font-weight: 700;
  font-size: 14px;
  color: var(--cd-text);
}
.style-combo-formula {
  font-size: 12px;
  color: var(--cd-text-secondary);
  font-style: italic;
}
.style-combo-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}
.style-combo-preview {
  margin-top: 12px;
  padding: 10px 14px;
  background-color: var(--cd-bg-card);
  border: 1px solid var(--cd-border);
  border-radius: 6px;
  display: flex;
  align-items: center;
  gap: 10px;
}
.preview-tag {
  font-size: 12px;
  font-weight: 600;
  color: var(--cd-primary);
  background-color: var(--cd-bg-soft);
  padding: 2px 8px;
  border-radius: 4px;
  flex-shrink: 0;
}
.preview-text {
  font-size: 13px;
  color: var(--cd-text);
  line-height: 1.5;
}
.opt-label {
  font-weight: 600;
  margin-right: 8px;
}
.opt-desc {
  color: var(--cd-text-secondary);
  font-size: 12px;
}
.custom-input {
  margin-top: 8px;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 20px;
}
.submit-btn {
  min-width: 140px;
  height: 44px;
  font-size: 15px;
}

.preview-card {
  padding: 24px;
  position: sticky;
  top: 20px;
}
.preview-card h3 {
  margin: 0 0 12px;
  font-size: 16px;
  font-weight: 700;
  color: var(--cd-text);
}
.preview-decor {
  display: flex;
  justify-content: center;
  align-items: flex-end;
  gap: 4px;
  margin-bottom: 12px;
  opacity: 0.85;
}
.preview-steps {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.preview-step {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 10px;
  border-radius: 8px;
  background-color: var(--cd-bg-soft);
  font-size: 13px;
  color: var(--cd-text);
  font-weight: 600;
  transition: opacity 0.2s, text-decoration 0.2s;
}
.preview-step.is-skipped {
  opacity: 0.5;
  text-decoration: line-through;
}
.preview-step.is-skipped .step-name {
  color: var(--cd-text-secondary);
}
.preview-num.is-skipped {
  background-color: var(--cd-text-secondary);
}
.skip-tag {
  margin-left: auto;
  font-size: 11px;
  font-weight: 500;
  color: var(--cd-text-secondary);
  background-color: var(--cd-bg-card);
  padding: 2px 8px;
  border-radius: 10px;
  border: 1px dashed var(--cd-border);
}
.step-badge {
  display: inline-block;
  font-size: 11px;
  font-weight: 500;
  color: var(--cd-primary);
  background-color: var(--cd-bg-soft);
  padding: 1px 6px;
  border-radius: 8px;
  margin-left: 4px;
}
.field-tip {
  font-size: 12px;
  color: var(--cd-text-secondary);
  margin-top: 4px;
  line-height: 1.4;
}
.exec-mode-row {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}
.inline-tip {
  margin-top: 0;
}
.preview-num {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background-color: var(--cd-primary);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.preview-tip {
  margin-top: 16px;
  padding: 10px 12px;
  border-radius: 6px;
  background-color: var(--cd-bg-soft);
  font-size: 12px;
  color: var(--cd-text-secondary);
  display: flex;
  gap: 6px;
  align-items: flex-start;
  line-height: 1.6;
}

@media (max-width: 1024px) {
  .task-create {
    grid-template-columns: 1fr;
  }
  .preview-card {
    position: static;
  }
}
@media (max-width: 768px) {
  .form-row {
    grid-template-columns: repeat(2, 1fr);
  }
  .style-combo-row {
    grid-template-columns: 1fr;
  }
}
</style>
