<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 200 200"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ transform: `rotate(${rotate}deg)`, opacity: opacity }"
  >
    <!-- 组合涂鸦：星星、爱心、箭头、圈圈、星星 -->
    <!-- 左上：多角星 -->
    <g v-if="type === 'all' || type === 'sparkle'">
      <path
        d="M40 40 L44 52 L56 54 L46 62 L50 74 L40 66 L30 74 L34 62 L24 54 L36 52 Z"
        :fill="primary"
        opacity="0.85"
      />
    </g>
    <!-- 右上：手绘爱心 -->
    <g v-if="type === 'all' || type === 'heart'">
      <path
        d="M150 42 C150 34 142 32 138 37 C136 33 128 32 126 38 C120 34 114 37 114 45 C114 55 134 66 138 66 C142 66 162 55 162 45 C162 40 158 37 155 36"
        :fill="primary"
        opacity="0.9"
      />
    </g>
    <!-- 左下：涂鸦箭头 -->
    <g v-if="type === 'all' || type === 'arrow'">
      <path
        d="M30 138 L78 138 M78 138 L66 126 M78 138 L66 150"
        :stroke="accent"
        stroke-width="3"
        stroke-linecap="round"
        fill="none"
      />
    </g>
    <!-- 右下：虚线圆圈 -->
    <g v-if="type === 'all' || type === 'circle'">
      <circle
        cx="150" cy="150" r="26"
        :stroke="line"
        stroke-width="2.2"
        stroke-dasharray="4 3"
        fill="none"
      />
      <circle cx="150" cy="150" r="5" :fill="primary" opacity="0.8" />
    </g>
    <!-- 中间：小星星 -->
    <g v-if="type === 'all' || type === 'stars'">
      <path
        d="M100 100 L103 108 L111 109 L105 114 L107 122 L100 118 L93 122 L95 114 L89 109 L97 108 Z"
        :fill="accent"
        opacity="0.9"
      />
    </g>
    <!-- 中间：手绘波浪线 -->
    <g v-if="type === 'all' || type === 'wave'">
      <path
        d="M60 100 Q75 92 90 100 T120 100 T150 100"
        :stroke="warn"
        stroke-width="3"
        stroke-linecap="round"
        fill="none"
        opacity="0.8"
      />
    </g>
    <!-- 中心：感叹气泡 -->
    <g v-if="type === 'all' || type === 'bubble'">
      <path
        d="M100 60 C100 54 106 50 112 50 C120 50 126 54 126 62 C126 66 124 70 120 72 L112 72 L108 78 L106 74 L102 72 C100 68 100 64 100 60 Z"
        :stroke="line"
        stroke-width="2"
        :fill="accent"
        opacity="0.25"
      />
    </g>
    <!-- 对角线：斜线组 -->
    <g v-if="type === 'all' || type === 'slash'">
      <line x1="50" y1="50" x2="62" y2="62" :stroke="primary" stroke-width="2" opacity="0.8" />
      <line x1="140" y1="140" x2="152" y2="152" :stroke="accent" stroke-width="2" opacity="0.8" />
      <line x1="64" y1="48" x2="76" y2="60" :stroke="warn" stroke-width="2" opacity="0.6" />
    </g>
  </svg>
</template>

<script setup lang="ts">
import { computed } from 'vue'

type DoodleType = 'all' | 'sparkle' | 'heart' | 'arrow' | 'circle' | 'stars' | 'wave' | 'bubble' | 'slash'

interface Props {
  size?: number
  rotate?: number
  type?: DoodleType
  opacity?: number
}
const props = withDefaults(defineProps<Props>(), {
  size: 140,
  rotate: 0,
  type: 'all',
  opacity: 0.85
})

const primary = computed(() => 'var(--cd-primary)')
const accent = computed(() => 'var(--cd-accent)')
const line = computed(() => 'var(--cd-border)')
const warn = computed(() => 'var(--cd-warning)')
</script>
