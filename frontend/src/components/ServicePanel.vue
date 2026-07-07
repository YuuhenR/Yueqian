<template>
  <section class="work-view">
    <div class="page-head">
      <div>
        <h1>办理中心</h1>
        <p>{{ store.currentSession?.title || '当前会话' }}</p>
      </div>
      <div class="head-actions">
        <button class="btn primary" type="button" @click="store.createSession">新建行程</button>
        <button class="btn danger" type="button" @click="confirmDelete = true">删除行程</button>
      </div>
    </div>

    <div class="service-layout">
      <aside class="session-list">
        <button
          v-for="session in store.sessions"
          :key="session.id"
          class="session-item"
          :class="{ active: session.id === store.currentSessionId }"
          type="button"
          @click="selectSession(session.id)"
        >
          <strong>{{ session.title }}</strong>
          <span>#{{ session.id }}</span>
        </button>
      </aside>

      <section class="chat-area" :class="{ 'has-selected-order': selectedOrder }">
        <div v-if="selectedOrder" class="selected-order">
          <strong>{{ selectedOrder.trainNo }}</strong>
          <span>{{ selectedOrder.departureStation }} → {{ selectedOrder.arrivalStation }}</span>
          <small>{{ selectedOrder.travelDate }} · {{ selectedOrder.status }}</small>
        </div>

        <div ref="messageBox" class="message-list">
          <div v-for="(message, index) in store.messages" :key="index" class="message" :class="message.role">
            <p>{{ message.content }}</p>
          </div>
        </div>

        <div v-if="pendingBooking" class="intent-confirm">
          <strong>{{ pendingBooking.departureStation }} → {{ pendingBooking.arrivalStation }}</strong>
          <span>{{ pendingBooking.travelDate }} · {{ timePreferenceLabel(pendingBooking.timePreference) || '不限时段' }} · {{ pendingBooking.passengerCount }}人 · {{ pendingBooking.seatType }}</span>
          <div>
            <button class="btn primary" type="button" @click="confirmPendingBooking">确认购票</button>
            <button class="btn" type="button" @click="pendingBooking = null">取消</button>
          </div>
        </div>

        <div class="quick-actions">
          <button type="button" @click="openBooking">我要购票</button>
          <button type="button" @click="weatherCurrent">天气</button>
          <button type="button" @click="requestRefund">退票</button>
          <button type="button" @click="sendQuick('帮我推荐一个目的地建议')">目的地</button>
        </div>

        <form class="composer" @submit.prevent="send">
          <button class="icon-btn" type="button" title="语音输入" @click="voiceInput"><Mic /></button>
          <button class="icon-btn" type="button" title="选择订单" @click="openOrderPicker('select')"><TicketCheck /></button>
          <textarea v-model="input" rows="1" placeholder="请输入出行需求，或使用上方业务按钮" />
          <button class="btn primary send-btn" type="submit">发送</button>
        </form>
      </section>
    </div>

    <div v-if="bookingOpen" class="modal-backdrop">
      <form class="neo-modal booking-modal" @submit.prevent="submitBooking">
        <header>
          <h2>购票信息</h2>
          <button type="button" class="modal-close" @click="bookingOpen = false">×</button>
        </header>
        <div class="saved-passenger">
          <label class="passenger-select">乘车人
            <select v-model="selectedPassengerKey" @change="useSelectedPassenger">
              <option value="">手动填写</option>
              <option v-for="(passenger, index) in passengerOptions" :key="passengerKey(passenger, index)" :value="passengerKey(passenger, index)">
                {{ passenger.passengerName }} · {{ passenger.maskedIdCard }}
              </option>
            </select>
          </label>
          <label class="remember-passenger">
            <input v-model="rememberPassenger" type="checkbox" />
            保存为常用乘车人
          </label>
        </div>
        <section class="ai-route-box">
          <div class="route-query">
            <label>出发站<input v-model="booking.departureStation" list="railway-stations" placeholder="广州南" required @input="loadStationOptions(booking.departureStation)" /></label>
            <label>到达站<input v-model="booking.arrivalStation" list="railway-stations" placeholder="昆明南" required @input="loadStationOptions(booking.arrivalStation)" /></label>
            <label>日期<input v-model="booking.travelDate" type="date" required /></label>
            <button class="btn primary" type="button" :disabled="routeLoading" @click="recommendRoutes">
              {{ routeLoading ? '查询中' : '查询车次' }}
            </button>
          </div>
          <datalist id="railway-stations">
            <option v-for="station in stationOptions" :key="station.code" :value="station.name">
              {{ station.city }} · {{ station.code }}
            </option>
          </datalist>
          <div v-if="routePreferenceText" class="route-filter-note">{{ routePreferenceText }}</div>
          <div v-if="routeSearched && !visibleRouteOptions.length && !routeLoading" class="route-empty">
            12306 未返回该站点日期的车次。
          </div>
          <div v-if="visibleRouteOptions.length" class="route-results" aria-label="可选车次">
            <button
              v-for="option in visibleRouteOptions"
              :key="option.trainNo"
              class="ai-train-card"
              :class="{ selected: booking.trainNo === option.trainNo && booking.departureStation === option.departureStation && booking.arrivalStation === option.arrivalStation }"
              type="button"
              @click="applyRoute(option)"
            >
              <strong>{{ option.trainNo }}</strong>
              <span>{{ option.departureTime }} → {{ option.arrivalTime }}</span>
              <small>{{ option.departureStation }} → {{ option.arrivalStation }}</small>
              <small>{{ option.duration }} · {{ option.seatType }} · {{ option.price ? option.price + ' 元' : '票价以确认页为准' }}</small>
              <em>{{ option.reason }}</em>
            </button>
          </div>
        </section>
        <div class="form-grid">
          <label>乘车人<input v-model="booking.passengerName" required /></label>
          <label>身份证<input v-model="booking.idCard" required /></label>
          <label>车次<input v-model="booking.trainNo" required /></label>
          <label>日期<input v-model="booking.travelDate" type="date" required /></label>
          <label>出发站<input v-model="booking.departureStation" required /></label>
          <label>到达站<input v-model="booking.arrivalStation" required /></label>
          <label>座位
            <select v-model="booking.seatType">
              <option>二等座</option>
              <option>一等座</option>
              <option>商务座</option>
              <option>硬座</option>
              <option>软座</option>
              <option>硬卧</option>
              <option>软卧</option>
            </select>
          </label>
          <label>张数<input v-model.number="booking.ticketCount" type="number" min="1" max="5" required /></label>
        </div>
        <p v-if="formError" class="error">{{ formError }}</p>
        <footer>
          <button class="btn" type="button" @click="bookingOpen = false">取消</button>
          <button class="btn primary" type="submit">生成订单</button>
        </footer>
      </form>
    </div>

    <div v-if="orderPickerOpen" class="modal-backdrop">
      <section class="neo-modal">
        <header>
          <h2>{{ orderPickerTitle }}</h2>
          <button type="button" class="modal-close" @click="orderPickerOpen = false">×</button>
        </header>
        <div class="picker-list">
          <button
            v-for="order in store.orders"
            :key="order.id"
            class="picker-order"
            :class="{ active: order.id === store.selectedOrderId }"
            type="button"
            @click="pickOrder(order.id)"
          >
            <strong>{{ order.trainNo }}</strong>
            <span>{{ order.departureStation }} → {{ order.arrivalStation }}</span>
            <small>{{ order.travelDate }} · {{ order.status }}</small>
          </button>
          <p v-if="!store.orders.length" class="empty-line">暂无订单</p>
        </div>
      </section>
    </div>

    <div v-if="confirmDelete" class="modal-backdrop">
      <section class="neo-modal confirm-modal">
        <header><h2>删除行程</h2></header>
        <p>删除后，该行程的历史会话将不可见。</p>
        <footer>
          <button class="btn" type="button" @click="confirmDelete = false">取消</button>
          <button class="btn danger" type="button" @click="deleteSession">确认删除</button>
        </footer>
      </section>
    </div>

    <div v-if="refundOpen" class="modal-backdrop">
      <section class="neo-modal confirm-modal">
        <header><h2>确认退票</h2></header>
        <p v-if="selectedOrder">
          {{ selectedOrder.trainNo }} {{ selectedOrder.departureStation }} → {{ selectedOrder.arrivalStation }}，{{ selectedOrder.travelDate }}。
        </p>
        <p v-else>请先选择需要退票的订单。</p>
        <footer>
          <button class="btn" type="button" @click="refundOpen = false">取消</button>
          <button class="btn danger" type="button" :disabled="!selectedOrder" @click="confirmRefund">确认退票</button>
        </footer>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { Mic, TicketCheck } from 'lucide-vue-next'
import { useAppStore } from '../stores/app'
import { assistRoutes, getPassengerProfile, getPassengerProfiles, parseRouteIntent, queryCityWeather, queryRouteWeather, savePassengerProfile, searchRoutes, searchStations, streamChat, type PassengerProfile, type RouteIntent, type StationOption, type TrainOption } from '../services/api'

const store = useAppStore()
const input = ref('')
const messageBox = ref<HTMLElement | null>(null)
const bookingOpen = ref(false)
const orderPickerOpen = ref(false)
const orderPickerMode = ref<'select' | 'weather' | 'refund'>('select')
const confirmDelete = ref(false)
const refundOpen = ref(false)
const formError = ref('')
const routeOptions = ref<TrainOption[]>([])
const routeLoading = ref(false)
const routeSearched = ref(false)
const stationOptions = ref<StationOption[]>([])
const savedPassenger = ref<PassengerProfile | null>(null)
const passengerOptions = ref<PassengerProfile[]>([])
const selectedPassengerKey = ref('')
const rememberPassenger = ref(true)
const timePreference = ref<'all' | 'morning' | 'noon' | 'afternoon' | 'evening'>('all')
const pendingBooking = ref<(RouteIntent & { sourceText: string }) | null>(null)

const booking = reactive({
  passengerName: '',
  idCard: '',
  trainNo: '',
  departureStation: '',
  arrivalStation: '',
  travelDate: '',
  seatType: '二等座',
  ticketCount: 1
})

const selectedOrder = computed(() => store.orders.find(item => item.id === store.selectedOrderId))
const exactTimeRouteOptions = computed(() => {
  const range = timeRange(timePreference.value)
  if (!range) return routeOptions.value
  return routeOptions.value.filter(option => {
    const minutes = timeToMinutes(option.departureTime)
    return minutes >= range[0] && minutes <= range[1]
  })
})
const visibleRouteOptions = computed(() => {
  if (exactTimeRouteOptions.value.length || timePreference.value === 'all') {
    return exactTimeRouteOptions.value
  }
  const target = timeTarget(timePreference.value)
  return [...routeOptions.value]
    .sort((a, b) => Math.abs(timeToMinutes(a.departureTime) - target) - Math.abs(timeToMinutes(b.departureTime) - target))
    .slice(0, 8)
    .sort((a, b) => timeToMinutes(a.departureTime) - timeToMinutes(b.departureTime))
})
const routePreferenceText = computed(() => {
  if (timePreference.value === 'all' || !routeOptions.value.length) return ''
  if (exactTimeRouteOptions.value.length) return timePreferenceLabel(timePreference.value)
  return `${timePreferenceLabel(timePreference.value)}附近`
})
const orderPickerTitle = computed(() => {
  if (orderPickerMode.value === 'refund') return '选择退票订单'
  if (orderPickerMode.value === 'weather') return '选择天气订单'
  return '选择订单'
})

onMounted(loadPassengerProfiles)

async function selectSession(id: number) {
  store.currentSessionId = id
  store.selectedOrderId = 0
  await store.loadMessages()
}

function openOrderPicker(mode: 'select' | 'weather' | 'refund' = 'select') {
  orderPickerMode.value = mode
  orderPickerOpen.value = true
}

async function pickOrder(id: number) {
  store.selectedOrderId = id
  orderPickerOpen.value = false
  if (orderPickerMode.value === 'refund') {
    refundOpen.value = true
  }
  if (orderPickerMode.value === 'weather') {
    await weatherCurrent()
  }
  orderPickerMode.value = 'select'
}

function openBooking() {
  loadPassengerProfiles()
  if (!booking.travelDate) booking.travelDate = nextTravelDate()
  bookingOpen.value = true
}

async function sendQuick(text: string) {
  if (text === '我要购票') {
    openBooking()
    return
  }
  await store.sendMessage(text)
  await scrollBottom()
}

function requestRefund() {
  if (!selectedOrder.value) {
    openOrderPicker('refund')
    return
  }
  refundOpen.value = true
}

async function confirmRefund() {
  if (!selectedOrder.value) return
  const order = selectedOrder.value
  refundOpen.value = false
  await store.refundOrder(order.id)
  store.pushAssistant(`退票已提交：${order.orderNo}`)
  await scrollBottom()
}

async function weatherCurrent() {
  try {
    if (!selectedOrder.value) {
      openOrderPicker('weather')
      return
    }
    const text = await store.orderWeather()
    store.pushAssistant(text)
    await scrollBottom()
  } catch (err) {
    store.pushAssistant(err instanceof Error ? err.message : '请先选择订单')
  }
}

async function submitBooking() {
  try {
    formError.value = ''
    await store.prepareOrder({ ...booking })
    if (rememberPassenger.value) await savePassenger()
    bookingOpen.value = false
    store.pushAssistant('已生成待确认订单，请在订单页面确认出票。')
  } catch (err) {
    formError.value = err instanceof Error ? err.message : '订单生成失败'
  }
}

async function recommendRoutes() {
  try {
    formError.value = ''
    routeSearched.value = true
    routeLoading.value = true
    routeOptions.value = []
    if (!booking.departureStation || !booking.arrivalStation || !booking.travelDate) {
      formError.value = '请填写出发站、到达站和日期'
      return
    }
    routeOptions.value = await searchRoutes({
      departureStation: booking.departureStation,
      arrivalStation: booking.arrivalStation,
      travelDate: booking.travelDate
    })
    if (!routeOptions.value.length) {
      formError.value = '12306 未返回该站点日期的车次'
      return
    }
    booking.departureStation = routeOptions.value[0].departureStation
    booking.arrivalStation = routeOptions.value[0].arrivalStation
  } catch (err) {
    formError.value = err instanceof Error ? err.message : '车次查询失败'
  } finally {
    routeLoading.value = false
  }
}

async function loadStationOptions(keyword: string) {
  if (!keyword.trim()) {
    stationOptions.value = []
    return
  }
  try {
    stationOptions.value = await searchStations(keyword.trim())
  } catch {
    stationOptions.value = []
  }
}

function applyRoute(option: TrainOption) {
  booking.trainNo = option.trainNo
  booking.departureStation = option.departureStation
  booking.arrivalStation = option.arrivalStation
  booking.seatType = option.seatType
}

function nextTravelDate() {
  const date = new Date()
  date.setDate(date.getDate() + 1)
  return date.toISOString().slice(0, 10)
}

function useSavedPassenger() {
  if (!savedPassenger.value) return
  booking.passengerName = savedPassenger.value.passengerName
  booking.idCard = savedPassenger.value.idCard
}

function useSelectedPassenger() {
  const passenger = passengerOptions.value.find((item, index) => passengerKey(item, index) === selectedPassengerKey.value)
  if (!passenger) {
    booking.passengerName = ''
    booking.idCard = ''
    return
  }
  booking.passengerName = passenger.passengerName
  booking.idCard = passenger.idCard
}

function passengerKey(passenger: PassengerProfile, index: number) {
  return `${index}:${passenger.passengerName}:${passenger.maskedIdCard}`
}

async function savePassenger() {
  if (!booking.passengerName || !booking.idCard) return
  savedPassenger.value = await savePassengerProfile({
    passengerName: booking.passengerName,
    idCard: booking.idCard
  })
  await loadPassengerProfiles()
}

async function loadPassengerProfiles() {
  try {
    savedPassenger.value = await getPassengerProfile()
    passengerOptions.value = await getPassengerProfiles()
  } catch {}
}

async function deleteSession() {
  confirmDelete.value = false
  await store.deleteCurrentSession()
}

async function send() {
  const text = input.value.trim()
  if (!text) return
  input.value = ''
  store.messages.push({ role: 'user', content: text })
  if (text.includes('退票')) {
    requestRefund()
    return
  }
  if (isWeatherText(text)) {
    await weatherFromText(text)
    return
  }
  const intent = await resolveRouteIntent(text)
  if (intent?.needsBookingPage && intent.departureStation && intent.arrivalStation) {
    showPendingBooking(intent, text)
    return
  }
  if (isBookingText(text) || isTravelIntent(text)) {
    const localIntent = routeIntentFromText(text)
    if (localIntent) {
      showPendingBooking(localIntent, text)
    } else {
      await openBookingFromText(text)
    }
    return
  }
  const reply = await streamTextWithoutDuplicateUser(text)
  const ticket = extractAiTicket(reply || '')
  if (ticket) showPendingBooking(routeIntentFromTicket(ticket), text)
  await scrollBottom()
}

function isBookingText(text: string) {
  return /购票|买票|订票|车票|订单|下单|车次|买.*票|订.*票|购.*票/.test(text)
}

function isTravelIntent(text: string) {
  if (isDestinationAdvice(text)) return false
  return Boolean(parseRouteText(text)) && (
    Boolean(parseTravelDate(text))
    || parseTimePreference(text) !== 'all'
    || /高铁|动车|火车|出发|一个人|一人|\d+\s*人/.test(text)
  )
}

function isWeatherText(text: string) {
  return ['天气', '下雨', '气温'].some(key => text.includes(key))
}

async function weatherFromText(text: string) {
  try {
    const route = parseRouteText(text)
    const result = route
      ? await queryRouteWeather(route.from, route.to)
      : await queryCityWeather(parseWeatherCity(text))
    store.pushAssistant(result)
  } catch (err) {
    store.pushAssistant(err instanceof Error ? err.message : '天气查询失败')
  }
  await scrollBottom()
}

async function openBookingFromText(text: string) {
  openBooking()
  timePreference.value = parseTimePreference(text)
  booking.ticketCount = parseTicketCount(text)
  booking.seatType = parseSeatPreference(text)
  const route = parseRouteText(text)
  if (route) {
    booking.departureStation = route.from
    booking.arrivalStation = route.to
  }
  const parsedDate = parseTravelDate(text)
  booking.travelDate = parsedDate || booking.travelDate || nextTravelDate()
  if (booking.departureStation && booking.arrivalStation && booking.travelDate) {
    await recommendRoutes()
    const advice = await routeAiAdvice(text)
    store.pushAssistant(advice || '已列出可选车次，请点选车次并核对乘车人信息后生成订单。')
    await scrollBottom()
  }
}

async function openBookingFromIntent(intent: RouteIntent, userText: string) {
  openBooking()
  timePreference.value = normalizeTimePreference(intent.timePreference)
  booking.ticketCount = intent.passengerCount || 1
  booking.seatType = intent.seatType || '二等座'
  booking.departureStation = intent.departureStation
  booking.arrivalStation = intent.arrivalStation
  booking.travelDate = intent.travelDate || nextTravelDate()
  await recommendRoutes()
  const advice = await routeAiAdvice(userText)
  store.pushAssistant(advice || '已按您的需求列出可选车次，请点选车次并确认乘车人后生成订单。')
  await scrollBottom()
}

async function confirmPendingBooking() {
  if (!pendingBooking.value) return
  const intent = pendingBooking.value
  pendingBooking.value = null
  await openBookingFromIntent(intent, intent.sourceText)
}

function showPendingBooking(intent: RouteIntent, sourceText: string) {
  const normalized: RouteIntent & { sourceText: string } = {
    ...intent,
    travelDate: intent.travelDate || nextTravelDate(),
    timePreference: normalizeTimePreference(intent.timePreference),
    passengerCount: intent.passengerCount || 1,
    seatType: intent.seatType || '二等座',
    sourceText
  }
  pendingBooking.value = normalized
  store.pushAssistant(`已整理购票信息：${normalized.departureStation} → ${normalized.arrivalStation}，${normalized.travelDate}，${timePreferenceLabel(normalized.timePreference) || '不限时段'}，${normalized.passengerCount}人。请确认后进入购票。`)
  scrollBottom()
}

async function openBookingFromAi(ticket: { trainNo?: string; departureStation?: string; arrivalStation?: string; travelDate?: string }) {
  openBooking()
  if (ticket.departureStation) booking.departureStation = ticket.departureStation
  if (ticket.arrivalStation) booking.arrivalStation = ticket.arrivalStation
  if (ticket.travelDate) booking.travelDate = ticket.travelDate
  if (ticket.trainNo) booking.trainNo = ticket.trainNo
  if (booking.departureStation && booking.arrivalStation && booking.travelDate) {
    await recommendRoutes()
    if (ticket.trainNo) {
      const matched = routeOptions.value.find(item => item.trainNo.toUpperCase() === ticket.trainNo!.toUpperCase())
      if (matched) applyRoute(matched)
    }
  }
  const advice = await routeAiAdvice('根据上一条建议整理购票信息')
  store.pushAssistant(advice || '已整理为购票信息，请选择车次和乘车人后生成待确认订单。')
  await scrollBottom()
}

async function resolveRouteIntent(text: string) {
  if (isDestinationAdvice(text)) return null
  if (!/到|去|至|->|→|购票|买票|订票|车票|车次|高铁|动车|火车/.test(text)) {
    const contextual = contextualRouteIntent(text)
    return contextual
  }
  try {
    return await parseRouteIntent(withRecentContext(text))
  } catch {
    return contextualRouteIntent(text)
  }
}

async function streamTextWithoutDuplicateUser(text: string) {
  const assistant = { role: 'assistant' as const, content: '' }
  store.messages.push(assistant)
  await streamChat({ sessionId: store.currentSessionId, message: text }, delta => {
    assistant.content += delta
  })
  return assistant.content
}

function normalizeTimePreference(value: string): 'all' | 'morning' | 'noon' | 'afternoon' | 'evening' {
  return ['morning', 'noon', 'afternoon', 'evening'].includes(value) ? value as any : 'all'
}

async function routeAiAdvice(userText: string) {
  if (!visibleRouteOptions.value.length) return ''
  try {
    return await assistRoutes({
      userText,
      departureStation: booking.departureStation,
      arrivalStation: booking.arrivalStation,
      travelDate: booking.travelDate,
      timePreference: routePreferenceText.value || '不限',
      options: visibleRouteOptions.value.slice(0, 12)
    })
  } catch {
    return ''
  }
}

function extractAiTicket(text: string) {
  const train = text.match(/\b[GDCKZT]\d{1,5}\b/i)?.[0]
  const date = parseAiDate(text)
  const route = text.match(/([\u4e00-\u9fa5]{2,12})\s*(?:\d{1,2}:\d{2})?\s*(?:→|->|到|至)\s*([\u4e00-\u9fa5]{2,12})/)
  if (!train || !route) return null
  return {
    trainNo: train.toUpperCase(),
    departureStation: normalizeStation(route[1]),
    arrivalStation: normalizeStation(route[2]),
    travelDate: date || booking.travelDate || nextTravelDate()
  }
}

function parseAiDate(text: string) {
  const parsed = parseTravelDate(text)
  if (parsed) return parsed
  const zh = text.match(/(20\d{2})年(\d{1,2})月(\d{1,2})日/)
  if (!zh) return ''
  return `${zh[1]}-${zh[2].padStart(2, '0')}-${zh[3].padStart(2, '0')}`
}

function lastAssistantText() {
  return [...store.messages].reverse().find(item => item.role === 'assistant')?.content || ''
}

function parseRouteText(text: string): { from: string; to: string } | null {
  if (isDestinationAdvice(text)) return null
  const cleanText = stripTravelMeta(text)
  const matched = cleanText.match(/(?:从)?([\u4e00-\u9fa5]{2,12})(?:到|去|至|->|→)([\u4e00-\u9fa5]{2,16})/)
  if (!matched) return null
  if (isUnclearDestination(matched[2])) return null
  if (isUnclearOrigin(matched[1])) return null
  return {
    from: normalizeStation(matched[1]),
    to: normalizeStation(matched[2])
  }
}

function isDestinationAdvice(text: string) {
  return /(去哪|去哪里|哪儿|哪里|什么地方|比较好|好玩|推荐|周末|休息|旅游|旅行|度假|散心|目的地)/.test(text)
    && !/(买票|购票|订票|车票|车次|下单|订单|退票)/.test(text)
}

function isUnclearDestination(value: string) {
  return /(哪|哪里|哪儿|何处|什么地方|比较好|好玩|推荐|周末|休息|旅游|旅行|一个人|\d+人)/.test(value)
}

function isUnclearOrigin(value: string) {
  return /^(我|我们|本人|这个|那个|周六|周日|周末|今天|明天|后天)/.test(value)
}

function withRecentContext(text: string) {
  const context = lastAssistantText()
  return context ? `上一轮助手回复：${context}\n用户当前输入：${text}` : text
}

function contextualRouteIntent(text: string): RouteIntent | null {
  if (!/(周六|周日|周末|今天|明天|后天|上午|中午|下午|晚上|一等座|二等座|商务座|一个人|一人|\d+\s*人)/.test(text)) return null
  const ticket = extractAiTicket(lastAssistantText())
  if (!ticket?.departureStation || !ticket.arrivalStation) return null
  return routeIntentFromTicket({
    ...ticket,
    travelDate: parseTravelDate(text) || ticket.travelDate || nextTravelDate()
  }, text)
}

function routeIntentFromText(text: string): (RouteIntent & { sourceText?: string }) | null {
  const route = parseRouteText(text)
  if (!route) return null
  return {
    needsBookingPage: true,
    departureStation: route.from,
    arrivalStation: route.to,
    travelDate: parseTravelDate(text) || nextTravelDate(),
    timePreference: parseTimePreference(text),
    passengerCount: parseTicketCount(text),
    seatType: parseSeatPreference(text)
  }
}

function routeIntentFromTicket(ticket: { trainNo?: string; departureStation?: string; arrivalStation?: string; travelDate?: string }, sourceText = ''): RouteIntent & { sourceText?: string } {
  return {
    needsBookingPage: true,
    departureStation: ticket.departureStation || '',
    arrivalStation: ticket.arrivalStation || '',
    travelDate: parseTravelDate(sourceText) || ticket.travelDate || nextTravelDate(),
    timePreference: parseTimePreference(sourceText),
    passengerCount: parseTicketCount(sourceText),
    seatType: parseSeatPreference(sourceText)
  }
}

function parseWeatherCity(text: string) {
  return normalizeStation(text.replace(/天气|下雨|气温|查询|查一下|我要|我想|帮我|今天|明天|后天/g, '').trim() || '北京')
}

function normalizeStation(value: string) {
  return value
    .replace(/\b20\d{2}[-/.年]\d{1,2}[-/.月]\d{1,2}日?\b/g, '')
    .replace(/(今天|明天|后天|早上|上午|中午|下午|晚上|夜间|时段|左右|之前|之后|以后|以前)/g, '')
    .replace(/(出发|车票|买票|购票|订票|我想|我要|帮我|请帮我|一张|两张|三张|的车次|车次|的票|票|订单|高铁|动车|火车)/g, '')
    .replace(/^从/, '')
    .trim()
}

function stripTravelMeta(text: string) {
  return text
    .replace(/\b20\d{2}[-/.]\d{1,2}[-/.]\d{1,2}\b/g, '')
    .replace(/20\d{2}年\d{1,2}月\d{1,2}日/g, '')
    .replace(/(今天|明天|后天)/g, '')
    .replace(/(早上|上午|中午|下午|晚上|夜间|时段|左右|之前|之后|以后|以前)/g, '')
    .replace(/(帮我|请帮我|我要|我想|查询|查一下|买|订|购|一张|两张|三张|的车次|车次|的票|车票|票|订单|出发)/g, '')
    .trim()
}

function parseTravelDate(text: string) {
  const iso = text.match(/\b20\d{2}[-/.]\d{1,2}[-/.]\d{1,2}\b/)
  if (iso) {
    const [year, month, day] = iso[0].split(/[-/.]/)
    return `${year}-${month.padStart(2, '0')}-${day.padStart(2, '0')}`
  }
  const zh = text.match(/(20\d{2})年(\d{1,2})月(\d{1,2})日/)
  if (zh) return `${zh[1]}-${zh[2].padStart(2, '0')}-${zh[3].padStart(2, '0')}`
  if (text.includes('后天')) return offsetDate(2)
  if (text.includes('明天')) return offsetDate(1)
  if (text.includes('今天')) return offsetDate(0)
  return ''
}

function parseTimePreference(text: string): 'all' | 'morning' | 'noon' | 'afternoon' | 'evening' {
  if (/早上|上午/.test(text)) return 'morning'
  if (/中午/.test(text)) return 'noon'
  if (/下午/.test(text)) return 'afternoon'
  if (/晚上|夜间/.test(text)) return 'evening'
  return 'all'
}

function parseTicketCount(text: string) {
  if (/一个人|一人|1\s*人|一张|1\s*张/.test(text)) return 1
  if (/两个人|二人|2\s*人|两张|二张|2\s*张/.test(text)) return 2
  if (/三个人|三人|3\s*人|三张|3\s*张/.test(text)) return 3
  const matched = text.match(/([1-5])\s*(?:人|张)/)
  return matched ? Number(matched[1]) : 1
}

function parseSeatPreference(text: string) {
  if (text.includes('商务座')) return '商务座'
  if (text.includes('一等座')) return '一等座'
  if (text.includes('硬座')) return '硬座'
  if (text.includes('软座')) return '软座'
  if (text.includes('硬卧')) return '硬卧'
  if (text.includes('软卧')) return '软卧'
  return '二等座'
}

function timeRange(value: 'all' | 'morning' | 'noon' | 'afternoon' | 'evening') {
  if (value === 'morning') return [6 * 60, 11 * 60 + 30]
  if (value === 'noon') return [11 * 60, 14 * 60 + 59]
  if (value === 'afternoon') return [13 * 60, 18 * 60 + 30]
  if (value === 'evening') return [18 * 60, 23 * 60]
  return null
}

function timeTarget(value: 'all' | 'morning' | 'noon' | 'afternoon' | 'evening') {
  if (value === 'morning') return 9 * 60
  if (value === 'noon') return 12 * 60 + 30
  if (value === 'afternoon') return 15 * 60 + 30
  if (value === 'evening') return 19 * 60 + 30
  return 12 * 60
}

function timePreferenceLabel(value: 'all' | 'morning' | 'noon' | 'afternoon' | 'evening') {
  if (value === 'morning') return '上午'
  if (value === 'noon') return '中午'
  if (value === 'afternoon') return '下午'
  if (value === 'evening') return '晚上'
  return ''
}

function timeToMinutes(value: string) {
  const matched = value.match(/^(\d{1,2}):(\d{2})$/)
  if (!matched) return 0
  return Number(matched[1]) * 60 + Number(matched[2])
}

function offsetDate(days: number) {
  const date = new Date()
  date.setDate(date.getDate() + days)
  return date.toISOString().slice(0, 10)
}

async function scrollBottom() {
  await nextTick()
  if (messageBox.value) messageBox.value.scrollTop = messageBox.value.scrollHeight
}

function voiceInput() {
  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition
  if (!SpeechRecognition) return
  const recognition = new SpeechRecognition()
  recognition.lang = 'zh-CN'
  recognition.onresult = (event: any) => {
    input.value = event.results[0][0].transcript
  }
  recognition.start()
}
</script>
