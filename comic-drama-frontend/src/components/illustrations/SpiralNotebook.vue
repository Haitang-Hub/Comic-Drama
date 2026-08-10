<template>
  <svg
    :width="size"
    :height="size * 0.78"
    viewBox="0 0 400 312"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ transform: `rotate(${rotate}deg)` }"
  >
    <!-- 本子底部阴影（纯线条无渐变） -->
    <rect
      x="18"
      y="28"
      width="364"
      height="256"
      rx="6"
      :stroke="lineColor"
      stroke-width="2"
      :fill="pageBgColor"
    />
    <!-- 线圈左页（翻开效果） -->
    <path
      d="M38 48 L200 48 L200 268 L38 268 Z"
      :fill="leftPageColor"
      :stroke="lineColor"
      stroke-width="2"
    />
    <!-- 线圈右页 -->
    <path
      d="M204 48 L370 48 L370 268 L204 268 Z"
      :fill="rightPageColor"
      :stroke="lineColor"
      stroke-width="2"
    />
    <!-- 线圈中缝 -->
    <line x1="202" y1="48" x2="202" y2="268" :stroke="lineColor" stroke-width="1.5" stroke-dasharray="4 3" />
    <!-- 顶部金属线圈 -->
    <g :stroke="coilColor" stroke-width="2.5" fill="none">
      <ellipse cx="60" cy="34" rx="10" ry="6" />
      <ellipse cx="90" cy="34" rx="10" ry="6" />
      <ellipse cx="120" cy="34" rx="10" ry="6" />
      <ellipse cx="150" cy="34" rx="10" ry="6" />
      <ellipse cx="180" cy="34" rx="10" ry="6" />
      <ellipse cx="210" cy="34" rx="10" ry="6" />
      <ellipse cx="240" cy="34" rx="10" ry="6" />
      <ellipse cx="270" cy="34" rx="10" ry="6" />
      <ellipse cx="300" cy="34" rx="10" ry="6" />
      <ellipse cx="330" cy="34" rx="10" ry="6" />
      <!-- 线圈竖线 -->
      <line x1="50" y1="34" x2="50" y2="44" />
      <line x1="70" y1="34" x2="70" y2="44" />
      <line x1="80" y1="34" x2="80" y2="44" />
      <line x1="100" y1="34" x2="100" y2="44" />
      <line x1="110" y1="34" x2="110" y2="44" />
      <line x1="130" y1="34" x2="130" y2="44" />
      <line x1="140" y1="34" x2="140" y2="44" />
      <line x1="160" y1="34" x2="160" y2="44" />
      <line x1="170" y1="34" x2="170" y2="44" />
      <line x1="190" y1="34" x2="190" y2="44" />
      <line x1="200" y1="34" x2="200" y2="44" />
      <line x1="220" y1="34" x2="220" y2="44" />
      <line x1="230" y1="34" x2="230" y2="44" />
      <line x1="250" y1="34" x2="250" y2="44" />
      <line x1="260" y1="34" x2="260" y2="44" />
      <line x1="280" y1="34" x2="280" y2="44" />
      <line x1="290" y1="34" x2="290" y2="44" />
      <line x1="310" y1="34" x2="310" y2="44" />
      <line x1="320" y1="34" x2="320" y2="44" />
      <line x1="340" y1="34" x2="340" y2="44" />
    </g>
    <!-- 左页横线 -->
    <g :stroke="accentColor" stroke-width="1" opacity="0.35">
      <line x1="60" y1="90" x2="180" y2="90" />
      <line x1="60" y1="116" x2="180" y2="116" />
      <line x1="60" y1="142" x2="180" y2="142" />
      <line x1="60" y1="168" x2="180" y2="168" />
      <line x1="60" y1="194" x2="180" y2="194" />
      <line x1="60" y1="220" x2="180" y2="220" />
    </g>
    <!-- 左页涂鸦：小漫画框 -->
    <rect x="74" y="100" width="46" height="32" rx="2" :stroke="primaryColor" stroke-width="1.5" fill="none" />
    <line x1="74" y1="132" x2="120" y2="132" :stroke="primaryColor" stroke-width="1.5" />
    <!-- 星星涂鸦 -->
    <path d="M152 152 L156 160 L164 161 L158 167 L160 175 L152 171 L144 175 L146 167 L140 161 L148 160 Z" :fill="primaryColor" opacity="0.8" />
    <!-- 右页竖线（便利贴边） -->
    <line x1="340" y1="70" x2="340" y2="250" :stroke="accentColor" stroke-width="1" stroke-dasharray="3 3" opacity="0.4" />
    <!-- 右页书写线条 -->
    <g :stroke="lineColor" stroke-width="1.2" stroke-linecap="round">
      <path d="M224 88 Q270 90 320 88" fill="none" />
      <path d="M224 108 Q280 110 332 108" fill="none" />
      <path d="M224 128 Q258 130 300 128" fill="none" />
      <path d="M224 148 Q276 150 324 148" fill="none" opacity="0.7" />
      <path d="M224 168 Q264 170 310 168" fill="none" opacity="0.5" />
    </g>
    <!-- 右页马克笔高亮 -->
    <rect
      x="222"
      y="122"
      width="68"
      height="10"
      rx="2"
      :fill="highlightColor"
      opacity="0.45"
    />
    <rect
      x="248"
      y="142"
      width="52"
      height="10"
      rx="2"
      :fill="highlightColor"
      opacity="0.45"
    />
    <!-- 角标小爱心 -->
    <path
      d="M344 240 C344 234 338 232 334 236 C332 233 326 232 324 237 C320 234 314 236 314 242 C314 250 330 258 334 258 C338 258 352 250 352 242 C352 239 350 237 348 236"
      :fill="primaryColor"
      opacity="0.7"
    />
  </svg>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  size?: number
  rotate?: number
}
const props = withDefaults(defineProps<Props>(), {
  size: 320,
  rotate: 0
})

const lineColor = computed(() => 'var(--cd-border)')
const primaryColor = computed(() => 'var(--cd-primary)')
const accentColor = computed(() => 'var(--cd-accent)')
const pageBgColor = computed(() => 'var(--cd-bg-card)')
const leftPageColor = computed(() => 'var(--cd-bg-soft)')
const rightPageColor = computed(() => 'var(--cd-bg-card)')
const coilColor = computed(() => 'var(--cd-text-secondary)')
const highlightColor = computed(() => 'var(--cd-warning)')
</script>
