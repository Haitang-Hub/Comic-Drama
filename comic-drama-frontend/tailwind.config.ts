import type { Config } from 'tailwindcss'

// 颜色引用 CSS 变量，由三套主题（soft/bright/dark）动态切换
const themeColors = {
  primary: 'var(--cd-primary)',
  'primary-hover': 'var(--cd-primary-hover)',
  accent: 'var(--cd-accent)',
  bg: 'var(--cd-bg)',
  'bg-card': 'var(--cd-bg-card)',
  'bg-soft': 'var(--cd-bg-soft)',
  text: 'var(--cd-text)',
  'text-secondary': 'var(--cd-text-secondary)',
  border: 'var(--cd-border)',
  success: 'var(--cd-success)',
  warning: 'var(--cd-warning)',
  danger: 'var(--cd-danger)'
}

export default {
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: themeColors,
      fontFamily: {
        sketch: '"Ma Shan Zheng", "Comic Sans MS", "Segoe UI", cursive',
        sans: '"Comic Sans MS", "Segoe UI", system-ui, sans-serif'
      },
      borderRadius: {
        sketch: '6px'
      },
      boxShadow: {
        // 硬阴影：绘画本速写风格 token
        sketch: '3px 3px 0 0 var(--cd-border)',
        'sketch-sm': '2px 2px 0 0 var(--cd-border)',
        'sketch-lg': '5px 5px 0 0 var(--cd-border)'
      }
    }
  },
  plugins: []
} satisfies Config
