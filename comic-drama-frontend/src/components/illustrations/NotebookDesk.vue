<template>
  <!-- 漫剧手账台：笔记本+铅笔+马克笔+便利贴 组合装饰 -->
  <svg
    :width="width"
    :height="height"
    viewBox="0 0 600 420"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
  >
    <!-- 背景：小桌子/垫板 -->
    <rect
      x="30"
      y="360"
      width="540"
      height="26"
      rx="6"
      :fill="deskColor"
      opacity="0.8"
    />
    <rect
      x="30"
      y="380"
      width="540"
      height="10"
      rx="4"
      :fill="deskSideColor"
      opacity="0.6"
    />

    <!-- 打开的线圈笔记本（主体） -->
    <g transform="translate(90, 80)">
      <!-- 本子底座 -->
      <rect
        x="-6"
        y="246"
        width="426"
        height="18"
        rx="4"
        :fill="deskSideColor"
        opacity="0.5"
      />
      <!-- 页面背景 -->
      <rect
        x="0"
        y="10"
        width="414"
        height="250"
        rx="6"
        :stroke="lineColor"
        stroke-width="2"
        :fill="rightPageColor"
      />
      <!-- 左页 -->
      <path
        d="M18 30 L207 30 L207 236 L18 236 Z"
        :fill="leftPageColor"
        :stroke="lineColor"
        stroke-width="2"
      />
      <!-- 右页 -->
      <path
        d="M211 30 L400 30 L400 236 L211 236 Z"
        :fill="rightPageColor"
        :stroke="lineColor"
        stroke-width="2"
      />
      <!-- 中缝线圈孔 -->
      <g :fill="lineColor" opacity="0.4">
        <circle cx="209" cy="50" r="3" />
        <circle cx="209" cy="80" r="3" />
        <circle cx="209" cy="110" r="3" />
        <circle cx="209" cy="140" r="3" />
        <circle cx="209" cy="170" r="3" />
        <circle cx="209" cy="200" r="3" />
      </g>
      <!-- 顶部金属线圈 -->
      <g :stroke="coilColor" stroke-width="2.2" fill="none">
        <ellipse cx="50" cy="24" rx="9" ry="5" />
        <ellipse cx="80" cy="24" rx="9" ry="5" />
        <ellipse cx="110" cy="24" rx="9" ry="5" />
        <ellipse cx="140" cy="24" rx="9" ry="5" />
        <ellipse cx="170" cy="24" rx="9" ry="5" />
        <ellipse cx="200" cy="24" rx="9" ry="5" />
        <ellipse cx="230" cy="24" rx="9" ry="5" />
        <ellipse cx="260" cy="24" rx="9" ry="5" />
        <ellipse cx="290" cy="24" rx="9" ry="5" />
        <ellipse cx="320" cy="24" rx="9" ry="5" />
        <ellipse cx="350" cy="24" rx="9" ry="5" />
        <ellipse cx="380" cy="24" rx="9" ry="5" />
      </g>
      <!-- 左页：漫画分镜框 -->
      <g :stroke="primaryColor" stroke-width="1.8" fill="none">
        <rect x="34" y="50" width="70" height="56" rx="2" />
        <rect x="114" y="50" width="70" height="56" rx="2" />
        <rect x="34" y="118" width="70" height="56" rx="2" />
        <rect x="114" y="118" width="70" height="56" rx="2" />
      </g>
      <!-- 分镜内小涂鸦 -->
      <g :stroke="lineColor" stroke-width="1.2" fill="none" stroke-linecap="round">
        <circle cx="69" cy="70" r="8" />
        <path d="M64 86 Q69 94 74 86" />
        <path d="M140 60 L160 78" />
        <path d="M160 60 L140 78" />
        <path d="M60 140 Q69 136 78 140" />
        <path d="M149 150 L138 166 L160 166 Z" :fill="accentColor" opacity="0.6" />
      </g>
      <!-- 左页：对话气泡 -->
      <path
        d="M34 190 C34 180 44 176 54 178 L90 178 C98 178 104 182 104 192 C104 196 100 196 96 198 L90 208 L86 200 L60 200 C44 200 34 196 34 190 Z"
        :fill="accentColor"
        opacity="0.3"
        :stroke="lineColor"
        stroke-width="1.5"
      />
      <path d="M50 188 Q60 186 80 188" :stroke="lineColor" stroke-width="1.2" fill="none" />
      <!-- 右页：故事大纲线条 -->
      <g :stroke="lineColor" stroke-width="1.2" stroke-linecap="round" fill="none">
        <path d="M228 54 Q280 52 340 54" />
        <path d="M228 72 Q290 74 364 72" />
        <path d="M228 90 Q270 88 330 90" opacity="0.8" />
        <path d="M228 108 Q286 110 350 108" opacity="0.7" />
        <path d="M228 126 Q262 124 320 126" opacity="0.6" />
      </g>
      <!-- 马克笔高亮 -->
      <rect x="226" y="66" width="90" height="10" rx="2" :fill="warnColor" opacity="0.5" />
      <rect x="254" y="120" width="72" height="10" rx="2" :fill="warnColor" opacity="0.5" />
      <!-- 右页勾选清单 -->
      <g :stroke="primaryColor" stroke-width="1.5" fill="none">
        <rect x="230" y="150" width="14" height="14" rx="2" />
        <path d="M232 157 L236 161 L242 154" stroke-width="2" />
        <rect x="230" y="174" width="14" height="14" rx="2" />
        <rect x="230" y="198" width="14" height="14" rx="2" />
        <path d="M232 204 L236 208 L242 201" stroke-width="2" />
      </g>
      <g :stroke="lineColor" stroke-width="1.2" fill="none" stroke-linecap="round">
        <line x1="252" y1="157" x2="310" y2="157" />
        <line x1="252" y1="181" x2="300" y2="181" opacity="0.7" />
        <line x1="252" y1="205" x2="318" y2="205" />
      </g>
      <!-- 页码 -->
      <text x="200" y="230" :fill="secondaryTextColor" font-size="10" font-family="Comic Sans MS" text-anchor="end" opacity="0.5">1</text>
      <text x="218" y="230" :fill="secondaryTextColor" font-size="10" font-family="Comic Sans MS" opacity="0.5">2</text>
    </g>

    <!-- 铅笔（斜放） -->
    <g transform="translate(40, 308) rotate(-22)">
      <!-- 铅笔杆 -->
      <rect x="0" y="0" width="160" height="26" rx="3" :fill="pencilBodyColor" :stroke="lineColor" stroke-width="2" />
      <line x1="40" y1="0" x2="40" y2="26" :stroke="warnColor" stroke-width="1.2" opacity="0.6" />
      <line x1="80" y1="0" x2="80" y2="26" :stroke="warnColor" stroke-width="1.2" opacity="0.6" />
      <line x1="120" y1="0" x2="120" y2="26" :stroke="warnColor" stroke-width="1.2" opacity="0.6" />
      <!-- 金属箍 -->
      <rect x="160" y="-2" width="18" height="30" rx="2" :fill="coilColor" :stroke="lineColor" stroke-width="2" />
      <!-- 橡皮 -->
      <rect x="178" y="0" width="22" height="26" rx="3" :fill="dangerColor" :stroke="lineColor" stroke-width="2" />
      <!-- 木头笔尖 -->
      <path d="M0 0 L-28 13 L0 26 Z" :fill="pencilWoodColor" :stroke="lineColor" stroke-width="2" />
      <path d="M-18 8 L-28 13 L-18 18 Z" :fill="lineColor" opacity="0.85" />
    </g>

    <!-- 马克笔（另一侧斜放） -->
    <g transform="translate(470, 320) rotate(18)">
      <!-- 笔盖 -->
      <rect x="0" y="0" width="64" height="32" rx="6" :fill="warnColor" :stroke="lineColor" stroke-width="2" />
      <rect x="14" y="-8" width="24" height="10" rx="3" :fill="warnColor" :stroke="lineColor" stroke-width="2" />
      <!-- 标签 -->
      <rect x="64" y="0" width="60" height="32" rx="2" :fill="rightPageColor" :stroke="lineColor" stroke-width="2" />
      <line x1="72" y1="10" x2="116" y2="10" :stroke="lineColor" stroke-width="1" opacity="0.5" />
      <line x1="72" y1="17" x2="108" y2="17" :stroke="lineColor" stroke-width="1" opacity="0.4" />
      <!-- 握把 -->
      <path d="M124 0 L150 0 L160 16 L150 32 L124 32 L134 16 Z" :fill="leftPageColor" :stroke="lineColor" stroke-width="2" />
      <!-- 笔尖 -->
      <path d="M150 0 L168 -2 L176 16 L168 34 L150 32 L160 16 Z" :fill="warnColor" :stroke="lineColor" stroke-width="2" />
      <!-- 墨水滴 -->
      <circle cx="180" cy="16" r="6" :fill="warnColor" opacity="0.7" />
      <circle cx="190" cy="10" r="2" :fill="warnColor" opacity="0.5" />
    </g>

    <!-- 便利贴（右上角） -->
    <g transform="translate(430, 20) rotate(8)">
      <!-- 胶带 -->
      <rect x="26" y="-4" width="50" height="18" :fill="accentColor" opacity="0.75" />
      <!-- 便签纸 -->
      <path d="M6 14 L96 14 L96 102 L80 118 L6 118 Z" :fill="stickyNoteColor" :stroke="lineColor" stroke-width="2" />
      <path d="M96 102 L80 118 L80 102 Z" :fill="leftPageColor" :stroke="lineColor" stroke-width="1.5" />
      <!-- 文字线 -->
      <g :stroke="lineColor" stroke-width="1.2" opacity="0.65" stroke-linecap="round">
        <line x1="20" y1="38" x2="82" y2="38" />
        <line x1="20" y1="54" x2="74" y2="54" />
        <line x1="20" y1="70" x2="80" y2="70" />
        <line x1="20" y1="86" x2="68" y2="86" />
      </g>
      <!-- 重点下划线 -->
      <path d="M20 62 Q36 64 52 62 T82 62" :stroke="warnColor" stroke-width="3" stroke-linecap="round" fill="none" opacity="0.6" />
      <!-- 手绘星星 -->
      <path d="M72 32 L74 36 L78 37 L75 40 L76 44 L72 42 L68 44 L69 40 L66 37 L70 36 Z" :fill="primaryColor" opacity="0.9" />
    </g>

    <!-- 轻涂鸦装饰 -->
    <g :stroke="lineColor" stroke-width="1.8" fill="none" stroke-linecap="round">
      <!-- 左下小箭头 -->
      <path d="M60 60 L80 60 M80 60 L72 52 M80 60 L72 68" :stroke="accentColor" opacity="0.85" />
      <!-- 右下大星星 -->
      <path
        d="M520 110 L524 122 L536 124 L528 132 L530 144 L520 138 L510 144 L512 132 L504 124 L516 122 Z"
        :fill="primaryColor"
        opacity="0.8"
        stroke="none"
      />
      <!-- 底部爱心 -->
      <path
        d="M300 48 C300 42 294 40 291 44 C289 41 283 40 282 45 C278 42 274 44 274 50 C274 57 288 64 291 64 C294 64 307 57 307 50 C307 46 304 44 302 43"
        :fill="dangerColor"
        opacity="0.8"
        stroke="none"
      />
    </g>
  </svg>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  width?: number
  height?: number
}
const props = withDefaults(defineProps<Props>(), {
  width: 520,
  height: 420
})

const lineColor = computed(() => 'var(--cd-border)')
const primaryColor = computed(() => 'var(--cd-primary)')
const accentColor = computed(() => 'var(--cd-accent)')
const warnColor = computed(() => 'var(--cd-warning)')
const dangerColor = computed(() => 'var(--cd-danger)')
const leftPageColor = computed(() => 'var(--cd-bg-soft)')
const rightPageColor = computed(() => 'var(--cd-bg-card)')
const stickyNoteColor = computed(() => 'var(--cd-bg-card)')
const coilColor = computed(() => 'var(--cd-text-secondary)')
const pencilBodyColor = computed(() => '#f7d774')
const pencilWoodColor = computed(() => '#f5deb3')
const deskColor = computed(() => 'var(--cd-bg-soft)')
const deskSideColor = computed(() => 'var(--cd-text-secondary)')
const secondaryTextColor = computed(() => 'var(--cd-text-secondary)')
</script>
