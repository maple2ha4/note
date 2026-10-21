import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': 'http://127.0.0.1:8080',
      '/oauth2': 'http://127.0.0.1:8080',
      '/login': 'http://127.0.0.1:8080',
    },
  },
  build: { chunkSizeWarningLimit: 1100 },
})
