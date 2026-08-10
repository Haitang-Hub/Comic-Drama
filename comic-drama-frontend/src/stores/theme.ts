import { defineStore } from 'pinia'

export type ThemeName = 'soft' | 'bright' | 'dark'

const STORAGE_KEY = 'cd_theme'

function applyTheme(theme: ThemeName) {
  const html = document.documentElement
  html.setAttribute('data-theme', theme)
}

/** 启动时从 localStorage 恢复主题并应用 */
export function initTheme() {
  const saved = localStorage.getItem(STORAGE_KEY) as ThemeName | null
  const theme: ThemeName = saved && ['soft', 'bright', 'dark'].includes(saved) ? saved : 'soft'
  applyTheme(theme)
}

export const useThemeStore = defineStore('theme', {
  state: () => ({
    current: (localStorage.getItem(STORAGE_KEY) as ThemeName) || 'soft'
  }),
  getters: {
    isDark: (s) => s.current === 'dark'
  },
  actions: {
    setTheme(theme: ThemeName) {
      this.current = theme
      applyTheme(theme)
      localStorage.setItem(STORAGE_KEY, theme)
    },
    toggle() {
      const order: ThemeName[] = ['soft', 'bright', 'dark']
      const idx = order.indexOf(this.current)
      this.setTheme(order[(idx + 1) % order.length])
    }
  }
})
