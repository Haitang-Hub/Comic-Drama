import { defineStore } from 'pinia'
import { login as loginApi, getUserInfo as getUserInfoApi, type LoginDTO, type UserInfo } from '@/api/auth'

interface UserState {
  token: string
  userInfo: UserInfo | null
  roles: string[]
  permissions: string[]
}

export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    token: '',
    userInfo: null,
    roles: [],
    permissions: []
  }),
  getters: {
    isLogin: (s) => !!s.token,
    isAdmin: (s) => s.roles.includes('ADMIN'),
    nickname: (s) => s.userInfo?.nickname || s.userInfo?.username || ''
  },
  actions: {
    async login(dto: LoginDTO) {
      const info = await loginApi(dto)
      this.token = info.tokenValue
      this.userInfo = info.userInfo
      this.roles = info.roles || []
      this.permissions = info.permissions || []
      // 同步到 localStorage 供 axios 拦截器与刷新后恢复
      localStorage.setItem('cd_token', this.token)
    },
    async refreshUserInfo() {
      const info = await getUserInfoApi()
      this.token = info.tokenValue
      this.userInfo = info.userInfo
      this.roles = info.roles || []
      this.permissions = info.permissions || []
      localStorage.setItem('cd_token', this.token)
    },
    hasRole(role: string) {
      return this.roles.includes(role)
    },
    hasPerm(perm: string) {
      return this.permissions.includes(perm)
    },
    logout() {
      this.token = ''
      this.userInfo = null
      this.roles = []
      this.permissions = []
      localStorage.removeItem('cd_token')
    }
  },
  persist: {
    key: 'cd_user',
    storage: localStorage
  }
})
