<template>
  <div class="not-found">
    <div class="nf-card sketch-card">
      <div class="nf-emoji">{{ is403 ? '🔒' : '🧭' }}</div>
      <div class="nf-code">{{ is403 ? '403' : '404' }}</div>
      <h2 class="nf-title">{{ is403 ? '无访问权限' : '页面走丢了' }}</h2>
      <p class="nf-desc">
        {{ is403 ? '抱歉，你没有访问该页面的权限' : '你访问的页面不存在或已被移除' }}
      </p>
      <div class="nf-actions">
        <button class="sketch-btn sketch-btn--ghost" @click="router.back()">返回上一页</button>
        <button class="sketch-btn" @click="router.replace('/')">回到首页</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const is403 = computed(() => route.name === 'Forbidden')
</script>

<style scoped>
.not-found {
  min-height: 80vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}
.nf-card {
  text-align: center;
  padding: 48px 56px;
  max-width: 440px;
}
.nf-emoji {
  font-size: 56px;
  margin-bottom: 8px;
}
.nf-code {
  font-size: 64px;
  font-weight: 800;
  color: var(--cd-primary);
  line-height: 1;
  letter-spacing: 2px;
}
.nf-title {
  margin: 16px 0 8px;
  font-size: 20px;
  font-weight: 700;
  color: var(--cd-text);
}
.nf-desc {
  margin: 0 0 28px;
  font-size: 14px;
  color: var(--cd-text-secondary);
}
.nf-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}
</style>
