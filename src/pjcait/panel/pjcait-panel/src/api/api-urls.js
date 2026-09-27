const collector = {
  health: {
    check: '/health'
  },
  data: {
    put: '/data/put',
    search: '/data/search',
    list: '/data/list',
  },
  timer: {
    cron: '/timer/cron',
    executeNow: '/timer/execute-now',
    pause: '/timer/pause',
    resume: '/timer/resume',
    state: '/timer/state',
    update: '/timer/update'
  },
  easyspider: {
    callback: '/easyspider/callback'
  },
  datasource: {
    list: '/datasource/list'
  },
  crawler: {
    list: '/crawler/list',
  },
  collector: {
    putUrls: '/collector/put-urls'
  },
  configure: {
    datasource: {
      custom: {
        add: '/configure/datasource/custom/add',
        delete: '/configure/datasource/custom/delete',
        update: '/configure/datasource/custom/update',
        setStatus: '/configure/datasource/custom/set-status',
      },
      easyspider: {
        add: '/configure/datasource/easyspider/add',
        delete: '/configure/datasource/easyspider/delete',
        update: '/configure/datasource/easyspider/update',
        setStatus: '/configure/datasource/easyspider/set-status',
      },
      gdelt: {
        setStatus: '/configure/datasource/gdelt/set-status',
      }
    },
    crawler: {
      custom: {
        add: '/configure/crawler/custom/add',
        delete: '/configure/crawler/custom/delete',
        update: '/configure/crawler/custom/update',
        setStatus: '/configure/crawler/custom/set-status',
      },
      easyspider: {
        add: '/configure/crawler/easyspider/add',
        delete: '/configure/crawler/easyspider/delete',
        update: '/configure/crawler/easyspider/update',
        setStatus: '/configure/crawler/easyspider/set-status',
      }
    }
  },
  status: {
    crawlTask: {
      all: '/status/crawl-task/all',
      list: '/status/crawl-task/list',
      detail: '/status/crawl-task'
    }
  }
};

const processor = {
  process: {
    run: '/run'
  },
  history: {
    list : '/history/list',
    detail: '/history/detail',
  }
}

const apiUrls = {
  backend: {
    listAllInstances: '/all-instances',
    listInstances: '/instances',
  },
  agented: {
    collector: collector,
    processor: processor,
  },
}

export {
  apiUrls
}
