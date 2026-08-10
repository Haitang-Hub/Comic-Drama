/**
 * 任务相关常量映射（与后端 TaskStatus / 8 步工作流对齐）
 */

/** 任务状态码：0排队 1生成中 2已完成 3失败 4已暂停 */
export enum TaskStatus {
  QUEUE = 0,
  RUNNING = 1,
  DONE = 2,
  FAILED = 3,
  PAUSED = 4
}

/** 步骤节点状态码：0待执行 1执行中 2成功 3失败 4批量中 5已暂停 6已跳过 */
export enum StepNodeStatus {
  PENDING = 0,
  RUNNING = 1,
  SUCCESS = 2,
  ERROR = 3,
  BATCHING = 4,
  PAUSED = 5,
  SKIPPED = 6
}

export interface StatusMeta {
  label: string
  /** Element Plus Tag type */
  type: 'info' | 'warning' | 'success' | 'danger' | 'primary'
  /** 速写主题色变量名 */
  colorVar: string
}

/** 状态码 → 展示元数据 */
export const TASK_STATUS_MAP: Record<number, StatusMeta> = {
  [TaskStatus.QUEUE]: { label: '排队中', type: 'info', colorVar: '--cd-text-secondary' },
  [TaskStatus.RUNNING]: { label: '生成中', type: 'warning', colorVar: '--cd-warning' },
  [TaskStatus.DONE]: { label: '已完成', type: 'success', colorVar: '--cd-success' },
  [TaskStatus.FAILED]: { label: '失败', type: 'danger', colorVar: '--cd-danger' },
  [TaskStatus.PAUSED]: { label: '已暂停', type: 'info', colorVar: '--cd-text-secondary' }
}

export function statusMeta(code?: number): StatusMeta {
  if (code == null) return { label: '未知', type: 'info', colorVar: '--cd-text-secondary' }
  return TASK_STATUS_MAP[code] ?? { label: '未知', type: 'info', colorVar: '--cd-text-secondary' }
}

/** 9 步工作流节点名称（currentStep 1~9） */
export const STEP_NAMES: Record<number, string> = {
  1: '故事摘要',
  2: '分镜脚本',
  3: '资产设计',
  4: '资产绘图',
  5: '衍生绘图',
  6: '分镜绘图',
  7: '配音合成',
  8: '视频生成',
  9: '视频合并'
}

export function stepName(step?: number): string {
  if (step == null || step <= 0) return '等待开始'
  return STEP_NAMES[step] ?? `步骤 ${step}`
}

/** 画幅选项 */
export const ASPECT_RATIOS = [
  { label: '16:9 横屏', value: '16:9' },
  { label: '9:16 竖屏', value: '9:16' },
  { label: '1:1 方形', value: '1:1' }
]

/** 分辨率选项 */
export const RESOLUTIONS = ['480p', '720p', '1080p', '2K', '4K']

/** 画风选项（基础视觉技法） */
export interface ArtStyleOption {
  value: string
  label: string
  desc: string
}

export const ART_STYLES: ArtStyleOption[] = [
  { value: 'realistic', label: '真人', desc: '写实人像，基于真实摄影质感' },
  { value: '2d', label: '2D', desc: '二维动画，赛璐璐/厚涂等手绘技法' },
  { value: '3d', label: '3D', desc: '三维建模，立体渲染质感' },
  { value: 'oil', label: '厚涂', desc: '油画笔触，厚重颜料堆叠' },
  { value: 'watercolor', label: '水彩', desc: '水彩画，清透晕染' },
  { value: 'pixel', label: '像素', desc: '像素点阵，复古游戏感' }
]

/** 风格选项（美学取向/文化调性） */
export interface VisualStyleOption {
  value: string
  label: string
  desc: string
}

export const VISUAL_STYLES: VisualStyleOption[] = [
  { value: 'chinese', label: '国风', desc: '水墨、汉服、东方意境' },
  { value: 'shinkai', label: '新海诚', desc: '通透光影、天空、城市、青春感' },
  { value: 'manhwa', label: '韩漫', desc: '人物比例、美型度、韩式漫画质感' },
  { value: 'dark_fairy', label: '暗黑童话', desc: '阴郁、怪诞、梦幻氛围' },
  { value: 'cyberpunk', label: '赛博朋克', desc: '霓虹灯、未来科技、反乌托邦' },
  { value: 'anime', label: '日式动漫', desc: '经典日式动画风格' }
]

/** 根据画风+风格组合获取最终视觉定位描述（支持自定义文本） */
export function getVisualPositioning(artValue?: string, styleValue?: string): string {
  if (!artValue && !styleValue) return ''
  const parts: string[] = []
  if (artValue) {
    const art = ART_STYLES.find(a => a.value === artValue)
    parts.push(art ? `${art.label}：${art.desc}` : `自定义画风：${artValue}`)
  }
  if (styleValue) {
    const style = VISUAL_STYLES.find(s => s.value === styleValue)
    parts.push(style ? `${style.label}：${style.desc}` : `自定义风格：${styleValue}`)
  }
  return parts.join(' + ')
}
