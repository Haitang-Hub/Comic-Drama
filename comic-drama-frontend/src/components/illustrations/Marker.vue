<template>
  <svg
    :width="size"
    :height="size * 0.34"
    viewBox="0 0 400 136"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ transform: `rotate(${rotate}deg)` }"
  >
    <!-- 笔盖 -->
    <rect
      x="286"
      y="38"
      width="90"
      height="54"
      rx="8"
      :fill="markerBodyColor"
      :stroke="lineColor"
      stroke-width="2"
    />
    <!-- 笔盖夹 -->
    <rect
      x="310"
      y="26"
      width="36"
      height="16"
      rx="4"
      :fill="markerBodyColor"
      :stroke="lineColor"
      stroke-width="2"
    />
    <!-- 笔盖顶 -->
    <rect
      x="360"
      y="42"
      width="16"
      height="46"
      rx="4"
      :fill="markerTipCapColor"
      :stroke="lineColor"
      stroke-width="2"
    />
    <!-- 笔杆（白色标签区） -->
    <rect
      x="186"
      y="38"
      width="100"
      height="54"
      rx="3"
      :fill="labelColor"
      :stroke="lineColor"
      stroke-width="2"
    />
    <!-- 标签文字（手绘线） -->
    <line x1="202" y1="56" x2="270" y2="56" :stroke="lineColor" stroke-width="1.2" opacity="0.7" stroke-linecap="round" />
    <line x1="202" y1="68" x2="260" y2="68" :stroke="lineColor" stroke-width="1.2" opacity="0.6" stroke-linecap="round" />
    <line x1="202" y1="80" x2="252" y2="80" :stroke="lineColor" stroke-width="1.2" opacity="0.5" stroke-linecap="round" />
    <!-- 笔杆握把 -->
    <path
      d="M138 38 L186 38 L186 92 L138 92 L120 65 Z"
      :fill="gripColor"
      :stroke="lineColor"
      stroke-width="2"
    />
    <!-- 握把防滑纹 -->
    <g :stroke="lineColor" stroke-width="0.8" opacity="0.3">
      <line x1="134" y1="46" x2="134" y2="84" />
      <line x1="142" y1="44" x2="142" y2="86" />
      <line x1="150" y1="42" x2="150" y2="88" />
      <line x1="158" y1="42" x2="158" y2="88" />
      <line x1="166" y1="42" x2="166" y2="88" />
      <line x1="174" y1="42" x2="174" y2="88" />
      <line x1="182" y1="42" x2="182" y2="88" />
    </g>
    <!-- 笔尖锥 -->
    <path
      d="M120 65 L96 65 L86 58 L86 72 L96 65 L120 65 Z"
      :fill="markerBodyColor"
      :stroke="lineColor"
      stroke-width="2"
    />
    <!-- 笔尖（毛毡头） -->
    <path
      d="M86 58 L64 52 L56 60 L56 70 L64 78 L86 72 Z"
      :fill="tipColor"
      :stroke="lineColor"
      stroke-width="2"
    />
    <!-- 墨水渗出效果 -->
    <path
      d="M56 60 L40 56 L34 60 L34 70 L40 74 L56 70 Z"
      :fill="inkSplashColor"
      opacity="0.55"
    />
    <!-- 高光小点 -->
    <circle cx="310" cy="52" r="3" :fill="labelColor" opacity="0.85" />
  </svg>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  size?: number
  rotate?: number
  color?: 'primary' | 'accent' | 'warn' | 'danger' | 'success'
}
const props = withDefaults(defineProps<Props>(), {
  size: 280,
  rotate: 0,
  color: 'warn'
})

const lineColor = computed(() => 'var(--cd-border)')
const markerBodyColor = computed(() => {
  if (props.color === 'primary') return 'var(--cd-primary)'
  if (props.color === 'accent') return 'var(--cd-accent)'
  if (props.color === 'warn') return 'var(--cd-warning)'
  if (props.color === 'danger') return 'var(--cd-danger)'
  if (props.color === 'success') return 'var(--cd-success)'
  return 'var(--cd-warning)'
})
const markerTipCapColor = computed(() => {
  if (props.color === 'primary') return 'var(--cd-primary-hover)'
  return 'var(--cd-text-secondary)'
})
const gripColor = computed(() => 'var(--cd-bg-soft)')
const labelColor = computed(() => 'var(--cd-bg-card)')
const tipColor = computed(() => {
  if (props.color === 'primary') return 'var(--cd-primary-hover)'
  if (props.color === 'accent') return 'var(--cd-accent)'
  if (props.color === 'warn') return 'var(--cd-warning)'
  if (props.color === 'danger') return 'var(--cd-danger)'
  if (props.color === 'success') return 'var(--cd-success)'
  return 'var(--cd-warning)'
})
const inkSplashColor = computed(() => {
  if (props.color === 'primary') return 'var(--cd-primary)'
  if (props.color === 'accent') return 'var(--cd-accent)'
  if (props.color === 'warn') return 'var(--cd-warning)'
  if (props.color === 'danger') return 'var(--cd-danger)'
  if (props.color === 'success') return 'var(--cd-success)'
  return 'var(--cd-warning)'
})
</script>
