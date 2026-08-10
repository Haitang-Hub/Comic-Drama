<template>
  <svg
    :width="size"
    :height="size * 1.1"
    viewBox="0 0 280 308"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ transform: `rotate(${rotate}deg)` }"
  >
    <!-- 胶带 -->
    <path
      d="M118 4 L166 2 L168 40 L120 42 Z"
      :fill="tapeColor"
      opacity="0.82"
    />
    <line x1="124" y1="10" x2="122" y2="38" :stroke="tapeLineColor" stroke-width="0.5" opacity="0.5" />
    <line x1="136" y1="6" x2="134" y2="40" :stroke="tapeLineColor" stroke-width="0.5" opacity="0.5" />
    <line x1="148" y1="6" x2="146" y2="40" :stroke="tapeLineColor" stroke-width="0.5" opacity="0.5" />
    <line x1="160" y1="10" x2="158" y2="38" :stroke="tapeLineColor" stroke-width="0.5" opacity="0.5" />
    <!-- 便利贴主体（带折角） -->
    <path
      d="M36 38 L244 38 L244 256 L206 294 L36 294 Z"
      :fill="noteColor"
      :stroke="lineColor"
      stroke-width="2"
    />
    <!-- 右下角折角 -->
    <path
      d="M244 256 L206 294 L206 256 Z"
      :fill="foldColor"
      :stroke="lineColor"
      stroke-width="1.5"
    />
    <!-- 便利贴纸纹点 -->
    <g :fill="lineColor" opacity="0.06">
      <circle cx="60" cy="60" r="1" />
      <circle cx="90" cy="80" r="1" />
      <circle cx="120" cy="64" r="1" />
      <circle cx="150" cy="88" r="1" />
      <circle cx="180" cy="60" r="1" />
      <circle cx="210" cy="78" r="1" />
      <circle cx="70" cy="120" r="1" />
      <circle cx="100" cy="140" r="1" />
      <circle cx="130" cy="124" r="1" />
      <circle cx="160" cy="148" r="1" />
      <circle cx="190" cy="120" r="1" />
      <circle cx="220" cy="138" r="1" />
      <circle cx="80" cy="180" r="1" />
      <circle cx="110" cy="200" r="1" />
      <circle cx="140" cy="184" r="1" />
      <circle cx="170" cy="208" r="1" />
      <circle cx="200" cy="180" r="1" />
      <circle cx="70" cy="240" r="1" />
      <circle cx="100" cy="260" r="1" />
      <circle cx="130" cy="244" r="1" />
      <circle cx="160" cy="264" r="1" />
    </g>
    <!-- 便签内容横线 -->
    <g :stroke="lineColor" stroke-width="1.2" stroke-linecap="round" opacity="0.65">
      <line x1="60" y1="86" x2="220" y2="86" />
      <line x1="60" y1="110" x2="210" y2="110" />
      <line x1="60" y1="134" x2="218" y2="134" />
      <line x1="60" y1="158" x2="200" y2="158" />
      <line x1="60" y1="182" x2="212" y2="182" />
      <line x1="60" y1="206" x2="196" y2="206" />
      <line x1="60" y1="230" x2="180" y2="230" />
    </g>
    <!-- 勾选框 -->
    <rect x="58" y="96" width="12" height="12" rx="2" :stroke="primaryColor" stroke-width="1.5" fill="none" />
    <path
      d="M60 102 L64 106 L70 99"
      :stroke="primaryColor"
      stroke-width="2"
      stroke-linecap="round"
      fill="none"
    />
    <!-- 手写爱心 -->
    <path
      d="M198 166 C198 160 192 158 189 162 C187 159 181 158 180 163 C176 160 171 162 171 168 C171 175 186 183 189 183 C192 183 207 175 207 168 C207 165 205 163 203 162"
      :fill="primaryColor"
      opacity="0.85"
    />
    <!-- 重点划线 -->
    <path
      d="M62 218 Q80 220 100 218 Q120 220 140 218"
      :stroke="highlightColor"
      stroke-width="4"
      stroke-linecap="round"
      fill="none"
      opacity="0.55"
    />
  </svg>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  size?: number
  rotate?: number
  color?: 'primary' | 'accent' | 'soft' | 'warn'
}
const props = withDefaults(defineProps<Props>(), {
  size: 200,
  rotate: 0,
  color: 'soft'
})

const lineColor = computed(() => 'var(--cd-border)')
const primaryColor = computed(() => 'var(--cd-primary)')
const highlightColor = computed(() => 'var(--cd-warning)')
const noteColor = computed(() => {
  if (props.color === 'primary') return 'var(--cd-bg-soft)'
  if (props.color === 'accent') return 'var(--cd-bg-soft)'
  if (props.color === 'warn') return 'var(--cd-bg-soft)'
  return 'var(--cd-bg-card)'
})
const foldColor = computed(() => 'var(--cd-bg-soft)')
const tapeColor = computed(() => {
  if (props.color === 'warn') return 'var(--cd-warning)'
  if (props.color === 'primary') return 'var(--cd-primary)'
  if (props.color === 'accent') return 'var(--cd-accent)'
  return 'var(--cd-accent)'
})
const tapeLineColor = computed(() => 'var(--cd-border)')
</script>
