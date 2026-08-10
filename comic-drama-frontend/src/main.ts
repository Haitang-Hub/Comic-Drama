import { createApp } from 'vue'
import { createPinia } from 'pinia'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import { initTheme } from './stores/theme'
import './styles/main.css'
// Element Plus 完整样式（Phase-1 简化样式引入，确保 ElMessage 等函数式组件样式不缺失）
import 'element-plus/dist/index.css'

// 在 Vue 挂载前应用持久化主题，避免首屏闪烁（FOUC）
initTheme()

const app = createApp(App)
const pinia = createPinia()
pinia.use(piniaPluginPersistedstate)

// 全局注册图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component as any)
}

app.use(pinia)
app.use(router)
app.mount('#app')
