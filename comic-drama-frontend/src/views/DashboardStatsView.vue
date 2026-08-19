<template>
  <div class="dashboard-stats" v-loading="loading">
    <div class="page-header">
      <h2>数据看板</h2>
      <div class="header-actions">
        <el-radio-group v-model="trendRange" size="default">
          <el-radio-button :value="7">近7天</el-radio-button>
          <el-radio-button :value="30">近30天</el-radio-button>
          <el-radio-button :value="90">近90天</el-radio-button>
        </el-radio-group>
        <button class="sketch-btn" @click="refreshData">
          <el-icon><Refresh /></el-icon>
          刷新
        </button>
      </div>
    </div>

    <div class="stat-overview">
      <div class="overview-card sketch-card">
        <div class="card-header">
          <span class="card-title">总任务数</span>
          <span class="card-change" :class="changeClass(stats.totalChange)">
            <el-icon><component :is="stats.totalChange >= 0 ? ArrowUp : ArrowDown" /></el-icon>
            {{ Math.abs(stats.totalChange) }}
          </span>
        </div>
        <div class="card-value" :style="{ color: 'var(--cd-primary)' }">{{ stats.totalTasks }}</div>
      </div>

      <div class="overview-card sketch-card">
        <div class="card-header">
          <span class="card-title">完成率</span>
          <span class="card-change" :class="changeClass(stats.rateChange)">
            <el-icon><component :is="stats.rateChange >= 0 ? ArrowUp : ArrowDown" /></el-icon>
            {{ formatPct(stats.rateChange) }}
          </span>
        </div>
        <div class="card-value" :style="{ color: 'var(--cd-success)' }">{{ formatPct(stats.successRate) }}</div>
      </div>

      <div class="overview-card sketch-card">
        <div class="card-header">
          <span class="card-title">失败率</span>
        </div>
        <div class="card-value" :style="{ color: 'var(--cd-danger)' }">{{ formatPct(stats.failureRate) }}</div>
      </div>

      <div class="overview-card sketch-card">
        <div class="card-header">
          <span class="card-title">平均耗时</span>
        </div>
        <div class="card-value" :style="{ color: 'var(--cd-accent)' }">{{ formatDuration(stats.avgTotalTime) }}</div>
      </div>
    </div>

    <div class="chart-row">
      <div class="chart-card sketch-card">
        <div class="chart-header">
          <h3>任务趋势</h3>
        </div>
        <div ref="trendChartRef" class="chart-container"></div>
      </div>

      <div class="chart-card sketch-card">
        <div class="chart-header">
          <h3>9 步平均耗时</h3>
        </div>
        <div ref="stepChartRef" class="chart-container"></div>
      </div>
    </div>

    <div class="chart-card sketch-card wide">
      <div class="chart-header">
        <h3>作品统计</h3>
      </div>
      <div ref="workChartRef" class="chart-container"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, watch, onUnmounted } from 'vue'
import { Refresh, ArrowUp, ArrowDown } from '@element-plus/icons-vue'
import {
  getDashboardStats,
  getDailyTrend,
  getStepTimeStats,
  getWorkStats,
  type DashboardStats,
  type TrendItem,
  type StepTimeStats,
  type WorkStats
} from '@/api/statistics'
import { useThemeStore } from '@/stores/theme'
import * as echarts from 'echarts'

const themeStore = useThemeStore()

const loading = ref(false)
const trendRange = ref(7)

const stats = ref<DashboardStats>({
  totalTasks: 0,
  successCount: 0,
  failureCount: 0,
  successRate: 0,
  failureRate: 0,
  avgTotalTime: 0,
  totalChange: 0,
  successChange: 0,
  rateChange: 0
})

const trendData = ref<TrendItem[]>([])
const stepTimeData = ref<StepTimeStats>({ stepTimes: {} })
const workStats = ref<WorkStats>({ totalWorks: 0, totalDuration: 0, resolutionDistribution: {} })

const trendChartRef = ref<HTMLDivElement>()
const stepChartRef = ref<HTMLDivElement>()
const workChartRef = ref<HTMLDivElement>()

let trendChart: echarts.ECharts | null = null
let stepChart: echarts.ECharts | null = null
let workChart: echarts.ECharts | null = null

function changeClass(v: number) {
  return v >= 0 ? 'positive' : 'negative'
}

function formatPct(v: number) {
  if (v == null) return '0%'
  return `${(v * 100).toFixed(1)}%`
}

function formatDuration(seconds: number) {
  if (!seconds) return '0s'
  if (seconds < 60) return `${seconds}s`
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  if (m < 60) return `${m}m ${s}s`
  const h = Math.floor(m / 60)
  return `${h}h ${m % 60}m`
}

function initCharts() {
  if (trendChartRef.value) {
    trendChart = echarts.init(trendChartRef.value)
  }
  if (stepChartRef.value) {
    stepChart = echarts.init(stepChartRef.value)
  }
  if (workChartRef.value) {
    workChart = echarts.init(workChartRef.value)
  }
  updateCharts()

  window.addEventListener('resize', handleResize)
}

function handleResize() {
  trendChart?.resize()
  stepChart?.resize()
  workChart?.resize()
}

function getThemeColor(varName: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(varName).trim() || '#ff9f1c'
}

function updateCharts() {
  const primary = getThemeColor('--cd-primary')
  const success = getThemeColor('--cd-success')
  const danger = getThemeColor('--cd-danger')
  const accent = getThemeColor('--cd-accent')
  const textColor = getThemeColor('--cd-text')
  const textSecondary = getThemeColor('--cd-text-secondary')
  const borderColor = getThemeColor('--cd-border')

  if (trendChart && trendData.value.length > 0) {
    trendChart.setOption({
      tooltip: {
        trigger: 'axis',
        backgroundColor: getThemeColor('--cd-bg-card'),
        borderColor: borderColor,
        textStyle: { color: textColor }
      },
      legend: {
        top: 0,
        data: ['总任务', '已完成', '失败'],
        textStyle: { color: textColor },
        itemGap: 24,
        itemWidth: 14,
        itemHeight: 14
      },
      grid: { left: 50, right: 30, top: 60, bottom: 30 },
      xAxis: {
        type: 'category',
        data: trendData.value.map((d) => d.statDate),
        axisLabel: { color: textSecondary },
        axisLine: { lineStyle: { color: borderColor } }
      },
      yAxis: {
        type: 'value',
        axisLabel: { color: textSecondary },
        splitLine: { lineStyle: { color: borderColor, type: 'dashed' } }
      },
      series: [
        {
          name: '总任务',
          type: 'line',
          data: trendData.value.map((d) => d.totalTaskCount),
          smooth: true,
          itemStyle: { color: primary },
          areaStyle: { color: primary + '33' }
        },
        {
          name: '已完成',
          type: 'line',
          data: trendData.value.map((d) => d.successCount),
          smooth: true,
          itemStyle: { color: success },
          areaStyle: { color: success + '33' }
        },
        {
          name: '失败',
          type: 'line',
          data: trendData.value.map((d) => d.failureCount),
          smooth: true,
          itemStyle: { color: danger },
          areaStyle: { color: danger + '33' }
        }
      ]
    })
  }

  if (stepChart) {
    const stepNames = ['大纲', '分镜', '角色', '绘图', '配音', '视频']
    const stepKeys = ['outline', 'storyboard', 'asset', 'image', 'audio', 'video']
    const stepColors = [primary, accent, '#2ec4b6', '#ff9f1c', '#e63946', '#6b5ce7']

    stepChart.setOption({
      tooltip: {
        trigger: 'axis',
        backgroundColor: getThemeColor('--cd-bg-card'),
        borderColor: borderColor,
        textStyle: { color: textColor },
        formatter: (params: any) => `${params[0].name}: ${formatDuration(params[0].value)}`
      },
      grid: { left: 70, right: 30, top: 30, bottom: 50 },
      xAxis: {
        type: 'category',
        data: stepNames,
        axisLabel: { color: textSecondary, rotate: 20 },
        axisLine: { lineStyle: { color: borderColor } }
      },
      yAxis: {
        type: 'value',
        name: '耗时',
        nameTextStyle: { color: textSecondary },
        axisLabel: {
          color: textSecondary,
          formatter: (v: number) => formatDuration(v)
        },
        splitLine: { lineStyle: { color: borderColor, type: 'dashed' } }
      },
      series: [
        {
          type: 'bar',
          data: stepKeys.map((key, i) => ({
            value: stepTimeData.value.stepTimes[key] || 0,
            itemStyle: { color: stepColors[i], borderRadius: [4, 4, 0, 0] }
          })),
          barWidth: '50%'
        }
      ]
    })
  }

  if (workChart) {
    const resolutions = Object.keys(workStats.value.resolutionDistribution || {
      '480p': 0, '720p': 0, '1080p': 0, '2K': 0, '4K': 0
    })
    const counts = Object.values(workStats.value.resolutionDistribution || {
      '480p': 0, '720p': 0, '1080p': 0, '2K': 0, '4K': 0
    })

    workChart.setOption({
      tooltip: {
        trigger: 'item',
        backgroundColor: getThemeColor('--cd-bg-card'),
        borderColor: borderColor,
        textStyle: { color: textColor }
      },
      legend: {
        orient: 'vertical',
        textStyle: { color: textColor },
        right: 20,
        top: 'middle'
      },
      series: [
        {
          name: '分辨率分布',
          type: 'pie',
          radius: ['40%', '70%'],
          center: ['40%', '50%'],
          avoidLabelOverlap: false,
          itemStyle: { borderColor: borderColor, borderWidth: 2 },
          label: { color: textColor },
          data: resolutions.map((r, i) => ({
            name: r,
            value: counts[i],
            itemStyle: { color: [primary, success, accent, '#ff9f1c', '#6b5ce7'][i] }
          }))
        }
      ]
    })
  }
}

async function loadData() {
  loading.value = true
  try {
    const [s, t, step, w] = await Promise.all([
      getDashboardStats(),
      getDailyTrend(trendRange.value),
      getStepTimeStats(),
      getWorkStats()
    ])
    stats.value = s
    trendData.value = t
    stepTimeData.value = step
    workStats.value = w
    await nextTick()
    updateCharts()
  } catch (e) {
    console.error('Failed to load dashboard stats', e)
  } finally {
    loading.value = false
  }
}

function refreshData() {
  loadData()
}

watch(trendRange, () => {
  loadData()
})

/* 主题切换时重新渲染图表，更新文字和坐标颜色 */
watch(
  () => themeStore.current,
  () => {
    nextTick(() => updateCharts())
  }
)

onMounted(async () => {
  await nextTick()
  initCharts()
  loadData()
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  stepChart?.dispose()
  workChart?.dispose()
})
</script>

<style scoped>
.dashboard-stats {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}
.page-header h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 800;
  color: var(--cd-text);
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.stat-overview {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}
.overview-card {
  padding: 20px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.card-title {
  font-size: 13px;
  color: var(--cd-text-secondary);
  font-weight: 600;
}
.card-change {
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 2px;
}
.card-change.positive {
  color: var(--cd-success);
}
.card-change.negative {
  color: var(--cd-danger);
}
.card-value {
  font-size: 32px;
  font-weight: 800;
  line-height: 1;
}

.chart-row {
  display: grid;
  grid-template-columns: 1.5fr 1fr;
  gap: 20px;
}
.chart-card {
  padding: 20px;
}
.chart-card.wide {
  padding: 20px;
}
.chart-header {
  margin-bottom: 16px;
}
.chart-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--cd-text);
}
.chart-container {
  width: 100%;
  height: 300px;
}

@media (max-width: 1024px) {
  .stat-overview {
    grid-template-columns: repeat(2, 1fr);
  }
  .chart-row {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 480px) {
  .stat-overview {
    grid-template-columns: 1fr;
  }
}
</style>
