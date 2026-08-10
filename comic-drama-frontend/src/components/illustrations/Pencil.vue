<template>
  <svg
    :width="size"
    :height="size * 0.32"
    viewBox="0 0 400 128"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ transform: `rotate(${rotate}deg)` }"
  >
    <!-- 铅笔杆 -->
    <rect
      x="110"
      y="42"
      width="220"
      height="38"
      rx="3"
      :fill="bodyColor"
      :stroke="lineColor"
      stroke-width="2"
    />
    <!-- 铅笔杆条纹（双色） -->
    <line x1="160" y1="42" x2="160" y2="80" :stroke="stripeColor" stroke-width="1.5" />
    <line x1="210" y1="42" x2="210" y2="80" :stroke="stripeColor" stroke-width="1.5" />
    <line x1="260" y1="42" x2="260" y2="80" :stroke="stripeColor" stroke-width="1.5" />
    <line x1="310" y1="42" x2="310" y2="80" :stroke="stripeColor" stroke-width="1.5" />
    <!-- 金属笔箍 -->
    <rect
      x="330"
      y="38"
      width="26"
      height="46"
      rx="2"
      :fill="ferruleColor"
      :stroke="lineColor"
      stroke-width="2"
    />
    <line x1="336" y1="38" x2="336" y2="84" :stroke="lineColor" stroke-width="0.8" opacity="0.6" />
    <line x1="344" y1="38" x2="344" y2="84" :stroke="lineColor" stroke-width="0.8" opacity="0.6" />
    <line x1="352" y1="38" x2="352" y2="84" :stroke="lineColor" stroke-width="0.8" opacity="0.6" />
    <!-- 橡皮头 -->
    <rect
      x="356"
      y="40"
      width="30"
      height="42"
      rx="4"
      :fill="eraserColor"
      :stroke="lineColor"
      stroke-width="2"
    />
    <line x1="360" y1="40" x2="360" y2="82" :stroke="lineColor" stroke-width="0.8" opacity="0.35" />
    <!-- 木头笔尖 -->
    <path
      d="M110 42 L70 61 L110 80 Z"
      :fill="woodColor"
      :stroke="lineColor"
      stroke-width="2"
    />
    <path
      d="M110 42 L70 61 L110 80 Z"
      :fill="woodGrainColor"
      opacity="0.3"
    />
    <!-- 石墨铅芯 -->
    <path
      d="M84 56 L70 61 L84 66 Z"
      :fill="leadColor"
      :stroke="lineColor"
      stroke-width="1.5"
    />
    <!-- 笔尖阴影线 -->
    <line x1="104" y1="50" x2="82" y2="59" :stroke="lineColor" stroke-width="0.8" opacity="0.3" />
    <line x1="104" y1="72" x2="82" y2="63" :stroke="lineColor" stroke-width="0.8" opacity="0.3" />
  </svg>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  size?: number
  rotate?: number
  variant?: 'yellow' | 'accent' | 'primary'
}
const props = withDefaults(defineProps<Props>(), {
  size: 280,
  rotate: 0,
  variant: 'yellow'
})

const lineColor = computed(() => 'var(--cd-border)')
const bodyColor = computed(() => {
  if (props.variant === 'primary') return 'var(--cd-primary)'
  if (props.variant === 'accent') return 'var(--cd-accent)'
  return '#f7d774'
})
const stripeColor = computed(() => 'var(--cd-bg-soft)')
const ferruleColor = computed(() => 'var(--cd-text-secondary)')
const eraserColor = computed(() => 'var(--cd-danger)')
const woodColor = computed(() => {
  if (props.variant === 'primary') return 'var(--cd-bg-soft)'
  if (props.variant === 'accent') return 'var(--cd-bg-soft)'
  return '#f5deb3'
})
const woodGrainColor = computed(() => 'var(--cd-border)')
const leadColor = computed(() => 'var(--cd-text)')
</script>
