import { createRouter, createWebHistory } from 'vue-router'
// 导入页面组件（修复路径和组件名）
import TradeDashboard from '@/views/TradeDashboard.vue'
import QAPage from '@/views/QAPage.vue'
import CorpusManagement from '@/views/CorpusManagement.vue'
import DataManagement from '@/views/DataManagement.vue'
import DataVisualization from '@/views/DataVisualization.vue'
import OntologyManagement from '@/views/OntologyManagement.vue'
import DataCollectionMain from '@/views/DataCollection/DataCollectionMain.vue'
import AjReportPage from '@/views/AjReportPage.vue'
import CrawlerNews from '@/views/CrawlerNews.vue'

// 定义路由规则
const routes = [
  {
    path: '/',
    redirect: '/login'
  },
  // 登录页（无需登录）
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { public: true, title: '登录' }
  },
  // 注册页（无需登录）
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue'),
    meta: { public: true, title: '注册' }
  },
  // 个人中心（需要登录）
  {
    path: '/user/profile',
    name: 'UserProfile',
    component: () => import('@/views/UserProfile.vue'),
    meta: { requiresAuth: true, title: '个人中心' }
  },
  {
    path: '/tradeDashboard',
    name: 'TradeDashboard',
    component: TradeDashboard,
    meta: { requiresAuth: true, title: '数据看板' }
  },
  {
    path: '/aj-report',
    name: 'AjReportPage',
    component: AjReportPage,
    meta: { requiresAuth: true, title: 'AJ-Report 大屏' }
  },
  {
    path: '/qa',
    name: 'QAPage',
    component: QAPage,
    meta: { requiresAuth: true, title: '智能问答' }
  },
  {
    path: '/corpusManagement',
    name: 'CorpusManagement',
    component: CorpusManagement,
    meta: { requiresAuth: true, title: '语料管理' }
  },
  {
    path: '/data',
    name: 'DataManagement',
    component: DataManagement,
    meta: { requiresAuth: true, title: '数据管理' }
  },
  {
    path: '/dataVisualization',
    name: 'DataVisualization',
    component: DataVisualization,
    meta: { requiresAuth: true, title: '图谱可视化' }
  },
  {
    path: '/dataCollection',
    component: DataCollectionMain,
    meta: { requiresAuth: true, title: '数据采集' },
    children: [
      {
        path: '',
        name: 'DataCollectionConsole',
        component: () => import('@/views/DataCollection/ConsolePage.vue'),
        meta: { requiresAuth: true, title: '采集控制台' }
      },
      {
        path: 'monitor',
        name: 'DataCollectionMonitor',
        component: () => import('@/views/DataCollection/MonitorPage.vue'),
        meta: { requiresAuth: true, title: '任务监控' }
      },
      {
        path: 'config',
        name: 'DataCollectionConfig',
        component: () => import('@/views/DataCollection/ConfigPage.vue'),
        meta: { requiresAuth: true, title: '采集配置' }
      }
    ]
  },
  {
    path: '/crawlerNews',
    name: 'CrawlerNews',
    component: CrawlerNews,
    meta: { requiresAuth: true, title: '新闻采集' }
  },
  {
    path: '/ontologyManagement',
    name: 'OntologyManagement',
    component: OntologyManagement,
    meta: { requiresAuth: true, title: '本体管理' }
  },
  // 本体项目详情页
  {
    path: '/ontology/project/:id',
    name: 'OntologyProject',
    component: () => import('@/views/OntologyProject.vue'),
    meta: {
      title: '本体项目详情',
      requiresAuth: true
    }
  },
  // 404 页面
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/Login.vue'),
    meta: { public: true }
  }
]

// 创建路由实例
const router = createRouter({
  history: createWebHistory(),
  routes,
  linkActiveClass: 'text-primary bg-indigo-50',
  linkExactActiveClass: 'text-primary bg-indigo-50'
})

// 路由守卫：检查登录状态
router.beforeEach((to, from, next) => {
  // 设置页面标题
  if (to.meta.title) {
    document.title = to.meta.title + ' - 中哈贸易'
  }

  // 获取 token
  const token = localStorage.getItem('token')

  // 访问公开页面（登录/注册）
  if (to.meta.public) {
    // 已登录用户访问登录/注册页，重定向到首页
    if (token && (to.path === '/login' || to.path === '/register')) {
      next('/tradeDashboard')
      return
    }
    next()
    return
  }

  // 访问根路径 /
  if (to.path === '/') {
    if (token) {
      next('/tradeDashboard')
    } else {
      next('/login')
    }
    return
  }

  // 访问需要登录的页面
  if (to.meta.requiresAuth) {
    if (!token) {
      // 未登录，重定向到登录页
      next('/login')
      return
    }
    next()
    return
  }

  next()
})

export default router
