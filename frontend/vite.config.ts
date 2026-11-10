import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // 开发时把 /api 转发到后端，避免跨域与额外的 CORS 配置
      '/api': {
        target: process.env.RELAY_OPS_API ?? 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
