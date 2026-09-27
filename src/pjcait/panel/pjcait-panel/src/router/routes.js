const routes = [
  {
    path: '/',
    component: () => import('layouts/MainLayout.vue'),
    children: [
      { path: '', component: () => import('pages/IndexPage.vue') },
      {
        path: 'job',
        component: () => import('pages/job/JobMainPage.vue'),
        children: [
          { path: '', component: () => import('pages/job/console/ConsolePage.vue') },
          { path: 'datasource', component: () => import('pages/job/datasource/DatasourcePage.vue') },
          { path: 'data', component: () => import('pages/job/status/StatusPage.vue') },
        ]
      },
      {
        path: 'collector',
        component: () => import('pages/collector/CollectorMain.vue'),
        children: [
          { path: '', component: () => import('pages/collector/console/ConsolePage.vue') },
          { path: 'crawler', component: () => import('pages/collector/crawler/CrawlerPage.vue') },
          { path: 'news', component: () => import('pages/collector/news/NewsPage.vue') },
          { path: 'data', component: () => import('pages/collector/status/StatusPage.vue') },
        ]
      },
      {
        path: 'processor',
        component: () => import('pages/processor/ProcessorMainPage.vue'),
        children: [
          { path: '', component: () => import('pages/processor/console/ConsolePage.vue') },
          { path: 'record', component: () => import('pages/processor/history/HistoryPage.vue') },
          { path: 'record/:id(\\d+)', component: () => import('pages/processor/history/HistoryDetailPage.vue') },

        ]
      }
    ]
  },

  // Always leave this as last one,
  // but you can also remove it
  {
    path: '/:catchAll(.*)*',
    component: () => import('pages/ErrorNotFound.vue')
  }
]

export default routes
