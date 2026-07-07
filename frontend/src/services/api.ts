export interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
}

export interface LoginResult {
  token: string
  username: string
  displayName: string
  role: 'USER' | 'ADMIN'
}

export interface Session {
  id: number
  userId: string
  title: string
  pinned: boolean
  createTime: string
  updateTime: string
}

export interface ChatMessage {
  id?: number
  sessionId?: number
  userId?: string
  role: 'user' | 'assistant'
  content: string
  toolName?: string
  createTime?: string
}

export interface Dashboard {
  sessionCount: number
  messageCount: number
  activeOrderCount: number
  refundedOrderCount: number
  securityHighlights: string[]
  aiCapabilities: string[]
}

export interface TicketOrder {
  id: number
  orderNo: string
  trainNo: string
  departureStation: string
  arrivalStation: string
  travelDate: string
  seatType: string
  seatNo: string
  price: number
  status: string
  refundFee?: number
}

export interface PendingTicketOrder {
  id: number
  passengerName: string
  trainNo: string
  departureStation: string
  arrivalStation: string
  travelDate: string
  seatType: string
  ticketCount: number
  estimatedPrice: number
}

export interface AdminMetrics {
  userCount: number
  orderCount: number
  activeOrderCount: number
  refundedOrderCount: number
  pendingOrderCount: number
  dailyOrders: { label: string; value: number }[]
  routeRanking: { label: string; value: number }[]
}

export interface Destination {
  station: string
  tag: string
  trainNo: string
  reason: string
}

export interface BookingPayload {
  sessionId?: number
  passengerName: string
  idCard: string
  trainNo: string
  departureStation: string
  arrivalStation: string
  travelDate: string
  seatType: string
  ticketCount: number
}

export interface TrainOption {
  trainNo: string
  departureStation: string
  arrivalStation: string
  departureTime: string
  arrivalTime: string
  duration: string
  seatType: string
  price: number
  reason: string
}

export interface StationOption {
  name: string
  code: string
  city: string
}

export interface RouteIntent {
  needsBookingPage: boolean
  departureStation: string
  arrivalStation: string
  travelDate: string
  timePreference: 'all' | 'morning' | 'noon' | 'afternoon' | 'evening'
  passengerCount: number
  seatType: string
}

export interface PassengerProfile {
  passengerName: string
  idCard: string
  maskedIdCard: string
}

const TOKEN_KEY = 'rail-jwt-token'
const USER_KEY = 'rail-user'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function getCurrentUser(): LoginResult | null {
  const raw = localStorage.getItem(USER_KEY)
  return raw ? JSON.parse(raw) as LoginResult : null
}

export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export async function login(username: string, password: string, expectedRole: 'USER' | 'ADMIN' = 'USER') {
  const res = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password, expectedRole })
  })
  const json = await res.json() as ApiResponse<LoginResult>
  if (!json.success) throw new Error(json.message)
  setToken(json.data.token)
  localStorage.setItem(USER_KEY, JSON.stringify(json.data))
  return json.data
}

export async function register(username: string, displayName: string, password: string) {
  const res = await fetch('/api/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, displayName, password })
  })
  const json = await res.json() as ApiResponse<void>
  if (!res.ok || !json.success) throw new Error(json.message)
}

export async function api<T>(url: string, options: RequestInit = {}): Promise<T> {
  if (!getToken()) throw new Error('请先登录')
  const res = await fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${getToken()}`,
      ...(options.headers || {})
    }
  })
  const json = await res.json() as ApiResponse<T>
  if (!res.ok || !json.success) throw new Error(json.message || '请求失败')
  return json.data
}

export async function searchRoutes(payload: { departureStation: string; arrivalStation: string; travelDate: string }) {
  return api<TrainOption[]>('/api/route/search', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export async function assistRoutes(payload: {
  userText: string
  departureStation: string
  arrivalStation: string
  travelDate: string
  timePreference?: string
  options: TrainOption[]
}) {
  return api<string>('/api/route/assist', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export async function parseRouteIntent(userText: string) {
  return api<RouteIntent>('/api/route/intent', {
    method: 'POST',
    body: JSON.stringify({ userText })
  })
}

export async function searchStations(keyword: string) {
  return api<StationOption[]>(`/api/route/stations?keyword=${encodeURIComponent(keyword)}`)
}

export async function queryCityWeather(city: string) {
  return api<string>(`/api/weather/city?city=${encodeURIComponent(city)}`)
}

export async function queryRouteWeather(from: string, to: string) {
  return api<string>(`/api/weather/route?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`)
}

export async function getPassengerProfile() {
  return api<PassengerProfile | null>('/api/passenger/profile')
}

export async function getPassengerProfiles() {
  return api<PassengerProfile[]>('/api/passenger/profile/list')
}

export async function savePassengerProfile(payload: { passengerName: string; idCard: string }) {
  return api<PassengerProfile>('/api/passenger/profile', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export async function streamChat(
  payload: { sessionId: number; userId?: string; message: string },
  onDelta: (text: string) => void
) {
  if (!getToken()) throw new Error('请先登录')
  const res = await fetch('/api/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${getToken()}`
    },
    body: JSON.stringify(payload)
  })
  if (!res.ok || !res.body) throw new Error('请求失败')
  const reader = res.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const events = buffer.split('\n\n')
    buffer = events.pop() || ''
    for (const event of events) {
      if (event.includes('event:done')) continue
      for (const line of event.split('\n')) {
        if (line.startsWith('data:')) {
          const data = line.slice(5).trim()
          if (data) onDelta(data)
        }
      }
    }
  }
}
