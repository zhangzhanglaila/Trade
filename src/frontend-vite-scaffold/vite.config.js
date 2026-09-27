// vite.config.js
import { defineConfig } from 'vite'

export default defineConfig({
  server: {
    port: 3000,
    historyApiFallback: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: path => path.replace(/^\/api/, '')
      }
    }
  }
})