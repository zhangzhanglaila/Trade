const { defineConfig } = require('@vue/cli-service')
const fs = require('fs')
const path = require('path')
console.log('🚀 vue.config.js 已加载')

// ============================================================
// 统一凭据：把项目根 config/.env 载入 process.env
//   该文件不入库（详见 config/README.md）。
//   带 VUE_APP_ 前缀的变量会被 Vue CLI 注入前端运行时。
// ============================================================
function loadTradeEnv () {
  const envPath = path.resolve(__dirname, '../../config/.env')
  if (!fs.existsSync(envPath)) {
    console.warn('[config] 未找到 config/.env，将使用代码内默认值。参见 config/README.md')
    return
  }
  for (const raw of fs.readFileSync(envPath, 'utf8').split(/\r?\n/)) {
    const line = raw.trim()
    if (!line || line.startsWith('#') || !line.includes('=')) continue
    const i = line.indexOf('=')
    const key = line.slice(0, i).trim()
    let val = line.slice(i + 1).trim()
    if (val.length >= 2 && (val[0] === '"' || val[0] === "'") && val[0] === val[val.length - 1]) {
      val = val.slice(1, -1)
    }
    if (key && process.env[key] === undefined) process.env[key] = val
  }
}
loadTradeEnv()

// 后端服务地址：本地开发默认 localhost。
// 跨机部署时用环境变量覆盖，例如：
//   VUE_APP_BACKEND_URL=http://10.0.0.5:8080 npm run serve
const BACKEND_URL = process.env.VUE_APP_BACKEND_URL || 'http://localhost:8080'
const TRAINING_URL = process.env.VUE_APP_TRAINING_URL || 'http://localhost:8081'

module.exports = {
  lintOnSave: false,
  publicPath: '/',
  devServer: {
    port: 3000,
    host: 'localhost',
    historyApiFallback: true,
    proxy: {
      '/ai': {
        target: BACKEND_URL,
        changeOrigin: true
      },
      '/ontology': {
        target: BACKEND_URL,
        changeOrigin: true,
        bypass: function(req, res, proxyOptions) {
          // 前端路由列表（页面路由，非API）
          const frontendRoutes = [
            '/ontologyManagement',
            '/ontology/project'
          ];

          // 如果是前端路由，绕过代理
          const isFrontendRoute = frontendRoutes.some(route =>
            req.url === route || req.url.startsWith(route + '/')
          );

          if (isFrontendRoute) {
            console.log('绕过代理(前端路由):', req.url);
            return '/index.html';
          }

          console.log('代理到后端:', req.url);
          return null;
        }
      },
      '/news': {
        target: BACKEND_URL,
        changeOrigin: true,
        bypass: function(req, res, proxyOptions) {
          const frontendRoutes = ['/newsManagement', '/newsDashboard'];
          if (frontendRoutes.some(route => req.url === route || req.url.startsWith(route + '/'))) {
            console.log('绕过代理(前端路由):', req.url);
            return '/index.html';
          }
          return null;
        }
      },
      '/logistics': {
        target: BACKEND_URL,
        changeOrigin: true
      },
      '/auth': {
        target: BACKEND_URL,
        changeOrigin: true
      },
      '/user': {
        target: BACKEND_URL,
        changeOrigin: true
      },
      '/aj-report': {
        target: BACKEND_URL,
        changeOrigin: true,
        bypass: function(req) {
          const frontendRoutes = ['/aj-report'];
          const isFrontendRoute = frontendRoutes.some(route => req.url === route || req.url.startsWith(route + '/'));
          if (isFrontendRoute && !req.url.startsWith('/aj-report/config') && !req.url.startsWith('/aj-report/go')) {
            return '/index.html';
          }
          return null;
        }
      },
      '/api/training': {
        target: TRAINING_URL,
        changeOrigin: true
      },
      '/api/data': {
        target: TRAINING_URL,
        changeOrigin: true
      },
      // 注意：/api/v1/graph 指向的 8083 服务在当前项目中并不存在，
      // 前端 src/api/Visual.js 亦未被任何页面引用（死代码）。保留仅作占位。
      '/api/v1/graph': {
        target: process.env.VUE_APP_GRAPH_URL || 'http://localhost:8083',
        changeOrigin: true
      },
      '/api': {
        target: BACKEND_URL,
        changeOrigin: true
      }
    }
  }
}
