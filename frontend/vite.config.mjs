import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    // 监听 0.0.0.0，使容器外（GitHub Codespaces 端口转发）可以访问
    host: true,
    port: 5173,
    strictPort: true,
    // Codespaces 通过 <名称>-5173.app.github.dev 之类的动态域名访问，
    // 后缀固定为 .app.github.dev，因此只需放行该后缀并保留 localhost。
    // 该选项在 Vite 5.4.12 之前不存在，旧版本会忽略它，行为等价于不做主机校验。
    allowedHosts: ['.app.github.dev', 'localhost', '127.0.0.1'],
    // 前端与后端同处一个容器，代理到本机 8080，浏览器只需访问 5173，因此不涉及跨域
    proxy: {
      '/api': { target: 'http://127.0.0.1:8080', changeOrigin: true },
      '/open-api': { target: 'http://127.0.0.1:8080', changeOrigin: true },
      '/public': { target: 'http://127.0.0.1:8080', changeOrigin: true }
    }
  }
})
