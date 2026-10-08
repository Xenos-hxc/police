import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  css: {
    preprocessorOptions: {
      scss: { api: 'modern-compiler' }
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: process.env.VITE_PROXY_TARGET || 'http://127.0.0.1:8080', changeOrigin: true }
    }
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    chunkSizeWarningLimit: 1000,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return undefined
          if (id.includes('element-plus') || id.includes('@element-plus')) return 'element-plus'
          if (id.includes('echarts') || id.includes('zrender')) return 'echarts'
          if (id.includes('/vue/') || id.includes('vue-router') || id.includes('pinia') || id.includes('@vue')) {
            return 'vue-vendor'
          }
          return 'vendor'
        }
      }
    }
  }
})
