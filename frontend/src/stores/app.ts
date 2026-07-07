import { defineStore } from 'pinia'
import { api, clearAuth, getCurrentUser, login, streamChat, type AdminMetrics, type BookingPayload, type ChatMessage, type Dashboard, type Destination, type LoginResult, type PendingTicketOrder, type Session, type TicketOrder } from '../services/api'

export const useAppStore = defineStore('app', {
  state: () => ({
    userId: '',
    user: getCurrentUser() as LoginResult | null,
    sessions: [] as Session[],
    currentSessionId: 0,
    messages: [] as ChatMessage[],
    dashboard: null as Dashboard | null,
    orders: [] as TicketOrder[],
    pendingOrders: [] as PendingTicketOrder[],
    selectedOrderId: 0,
    destinations: [] as Destination[],
    adminMetrics: null as AdminMetrics | null,
    loading: false
  }),
  getters: {
    currentSession(state) {
      return state.sessions.find(item => item.id === state.currentSessionId)
    }
  },
  actions: {
    async login(username: string, password: string, expectedRole: 'USER' | 'ADMIN' = 'USER') {
      this.user = await login(username, password, expectedRole)
      this.userId = this.user.username
    },
    logout() {
      clearAuth()
      this.user = null
      this.userId = ''
      this.sessions = []
      this.currentSessionId = 0
      this.messages = []
      this.orders = []
      this.pendingOrders = []
      this.selectedOrderId = 0
      this.adminMetrics = null
    },
    async init() {
      const user = getCurrentUser()
      if (!user) throw new Error('请先登录')
      this.user = user
      this.userId = user.username
      await Promise.all([this.loadSessions(), this.loadDashboard(), this.loadOrders(), this.loadPendingOrders(), this.loadDestinations()])
      if (this.user?.role === 'ADMIN') await this.loadAdminMetrics()
    },
    async loadSessions() {
      this.sessions = await api<Session[]>('/api/session/list')
      if (!this.sessions.length) {
        const created = await api<Session>('/api/session', {
          method: 'POST',
          body: JSON.stringify({ title: '我的行程咨询' })
        })
        this.sessions = [created]
      }
      if (!this.currentSessionId || !this.sessions.some(item => item.id === this.currentSessionId)) {
        this.currentSessionId = this.sessions[0].id
      }
      await this.loadMessages()
    },
    async createSession() {
      const created = await api<Session>('/api/session', {
        method: 'POST',
        body: JSON.stringify({ title: '新的行程' })
      })
      this.currentSessionId = created.id
      this.selectedOrderId = 0
      await this.loadSessions()
      await this.loadDashboard()
    },
    async deleteCurrentSession() {
      if (!this.currentSessionId) return
      await api<void>(`/api/session/${this.currentSessionId}`, { method: 'DELETE' })
      this.currentSessionId = 0
      await this.loadSessions()
      await this.loadDashboard()
    },
    async loadMessages() {
      if (!this.currentSessionId) return
      this.messages = await api<ChatMessage[]>(`/api/message/${this.currentSessionId}`)
      if (!this.messages.length) {
        this.messages = [{ role: 'assistant', content: '请描述本次出行需求。' }]
      }
    },
    pushAssistant(content: string) {
      this.messages.push({ role: 'assistant', content })
    },
    async sendMessage(text: string) {
      if (!text.trim() || !this.currentSessionId) return ''
      this.messages.push({ role: 'user', content: text })
      const assistant: ChatMessage = { role: 'assistant', content: '' }
      this.messages.push(assistant)
      await streamChat({ sessionId: this.currentSessionId, message: text }, delta => {
        assistant.content += delta
      })
      await Promise.all([this.loadOrders(), this.loadPendingOrders(), this.loadDashboard()])
      return assistant.content
    },
    async loadDashboard() {
      this.dashboard = await api<Dashboard>('/api/dashboard')
    },
    async loadOrders() {
      this.orders = await api<TicketOrder[]>('/api/order/list')
      if (this.selectedOrderId && !this.orders.some(item => item.id === this.selectedOrderId)) this.selectedOrderId = 0
    },
    async loadPendingOrders() {
      this.pendingOrders = await api<PendingTicketOrder[]>('/api/order/pending')
    },
    async confirmPending(id: number) {
      await api<TicketOrder>(`/api/order/pending/${id}/confirm`, { method: 'POST' })
      await this.loadOrders()
      await this.loadPendingOrders()
    },
    async prepareOrder(payload: BookingPayload) {
      await api<PendingTicketOrder>('/api/order/prepare', {
        method: 'POST',
        body: JSON.stringify({ ...payload, sessionId: this.currentSessionId || payload.sessionId })
      })
      await Promise.all([this.loadPendingOrders(), this.loadDashboard()])
    },
    async refundOrder(id: number) {
      await api<TicketOrder>(`/api/order/${id}/refund`, { method: 'POST' })
      await Promise.all([this.loadOrders(), this.loadDashboard()])
    },
    async orderWeather(id?: number) {
      const orderId = id || this.selectedOrderId
      if (!orderId) throw new Error('请先选择订单')
      return api<string>(`/api/order/${orderId}/weather`)
    },
    async loadAdminMetrics() {
      this.adminMetrics = await api<AdminMetrics>('/api/admin/metrics')
    },
    async loadDestinations() {
      this.destinations = await api<Destination[]>('/api/dashboard/destinations')
    }
  }
})
