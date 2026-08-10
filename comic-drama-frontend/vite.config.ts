import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import path from 'node:path'

// 漫剧AI生成Agent · 前端
export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      imports: ['vue', 'vue-router', 'pinia'],
      resolvers: [ElementPlusResolver()],
      dts: 'src/auto-imports.d.ts',
      eslintrc: { enabled: false }
    }),
    Components({
      resolvers: [ElementPlusResolver()],
      dts: 'src/components.d.ts'
    })
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  css: {
    preprocessorOptions: {
      scss: {
        // 全局注入速写风格 mixin 变量（如需要可扩展）
        additionalData: `@use "@/styles/sketch-vars.scss" as *;`
      }
    }
  },
  server: {
    host: '127.0.0.1',
    port: 5170,
    proxy: {
      // 所有业务请求经网关 :8070 转发到各微服务
      '/auth': { target: 'http://127.0.0.1:8070', changeOrigin: true },
      '/api': { target: 'http://127.0.0.1:8070', changeOrigin: true },
      // WebSocket 代理到网关
      '/ws': {
        target: 'ws://127.0.0.1:8070',
        ws: true,
        changeOrigin: true
      }
    }
  }
})
