import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '登录', public: true }
  },
  {
        path: '/',
        component: () => import('@/layouts/DashboardLayout.vue'),
        redirect: '/dashboard',
        children: [
          {
            path: 'dashboard',
            name: 'Dashboard',
            component: () => import('@/views/DashboardView.vue'),
            meta: { title: '仪表盘' }
          },
          {
            path: 'dashboard/stats',
            name: 'DashboardStats',
            component: () => import('@/views/DashboardStatsView.vue'),
            meta: { title: '数据看板' }
          },
          {
            path: 'task',
            name: 'TaskList',
            component: () => import('@/views/TaskListView.vue'),
            meta: { title: '我的任务' }
          },
          {
            path: 'task/create',
            name: 'TaskCreate',
            component: () => import('@/views/TaskCreateView.vue'),
            meta: { title: '创建任务' }
          },
          {
            path: 'task/:id',
            name: 'TaskDetail',
            component: () => import('@/views/TaskDetailView.vue'),
            meta: { title: '任务详情' }
          },
          {
            path: 'work',
            name: 'WorkList',
            component: () => import('@/views/WorkListView.vue'),
            meta: { title: '作品管理' }
          },
          {
            path: 'work/:id',
            name: 'WorkDetail',
            component: () => import('@/views/WorkDetailView.vue'),
            meta: { title: '作品详情' }
          },
          {
            path: 'profile',
            name: 'Profile',
            component: () => import('@/views/ProfileView.vue'),
            meta: { title: '个人中心' }
          },
          {
            path: 'admin',
            name: 'Admin',
            component: () => import('@/views/AdminView.vue'),
            meta: { title: '管理后台', roles: ['ADMIN'] }
          }
        ]
      },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { title: '无权限', public: true }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { title: '页面不存在', public: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, _from, next) => {
  const userStore = useUserStore()
  // 设置标题
  document.title = to.meta.title ? `${to.meta.title} · 漫剧AI` : '漫剧AI生成Agent'

  const isPublic = to.meta.public === true
  if (isPublic) {
    // 已登录用户访问登录页 → 跳首页
    if (to.name === 'Login' && userStore.isLogin) {
      return next('/')
    }
    return next()
  }
  // 受保护路由
  if (!userStore.isLogin) {
    return next({ path: '/login', query: { redirect: to.fullPath } })
  }
  // 角色守卫示例（Phase-1 仅基础路由，预留）
  if (to.meta.roles && !(to.meta.roles as string[]).some((r) => userStore.hasRole(r))) {
    return next('/403')
  }
  next()
})

export default router
