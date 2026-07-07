<template>
  <section class="work-view">
    <div class="page-head">
      <div>
        <h1>订单</h1>
        <p>{{ store.orders.length + store.pendingOrders.length }} 条记录</p>
      </div>
      <button class="btn primary" type="button" @click="refresh">刷新</button>
    </div>

    <div class="order-board scroll-frame">
      <section v-if="store.pendingOrders.length" class="order-section">
        <h2>待确认</h2>
        <div class="order-card pending-card" v-for="item in store.pendingOrders" :key="item.id">
          <div class="order-mark">待</div>
          <div>
            <strong>{{ item.trainNo }}</strong>
            <p>{{ stationLabel(item.departureStation) }} → {{ stationLabel(item.arrivalStation) }}</p>
            <span>{{ item.travelDate }} · {{ item.seatType }} · {{ item.ticketCount }} 张</span>
          </div>
          <div class="order-price">{{ item.estimatedPrice }} 元</div>
          <button class="btn primary" type="button" @click="store.confirmPending(item.id)">确认出票</button>
        </div>
      </section>

      <section class="order-section">
        <h2>已生成</h2>
        <div v-if="!store.orders.length" class="empty-line">暂无订单</div>
        <div
          class="order-card"
          :class="{ refunded: order.status === '已退票' }"
          v-for="order in store.orders"
          :key="order.id"
        >
          <div class="order-mark">{{ order.trainNo.slice(0, 1) }}</div>
          <div>
            <strong>{{ order.trainNo }}</strong>
            <p>{{ stationLabel(order.departureStation) }} → {{ stationLabel(order.arrivalStation) }}</p>
            <span>{{ order.travelDate }} · {{ order.seatType }} {{ order.seatNo }}</span>
            <small>{{ order.orderNo }}</small>
          </div>
          <div class="order-price">{{ order.price }} 元</div>
          <div class="order-actions">
            <span class="status" :class="{ refund: order.status === '已退票' }">{{ order.status }}</span>
            <button class="btn" type="button" @click="weather(order.id)">天气</button>
            <button v-if="order.status !== '已退票'" class="btn danger" type="button" @click="refundTarget = order.id">退票</button>
          </div>
        </div>
      </section>
    </div>

    <div v-if="weatherText" class="modal-backdrop">
      <section class="neo-modal">
        <header>
          <h2>天气</h2>
          <button type="button" class="modal-close" @click="weatherText = ''">×</button>
        </header>
        <p class="weather-text">{{ weatherText }}</p>
      </section>
    </div>

    <div v-if="refundTarget" class="modal-backdrop">
      <section class="neo-modal confirm-modal">
        <header><h2>确认退票</h2></header>
        <p>退票后订单状态将更新，手续费按规则计算。</p>
        <footer>
          <button class="btn" type="button" @click="refundTarget = 0">取消</button>
          <button class="btn danger" type="button" @click="refund">确认退票</button>
        </footer>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useAppStore } from '../stores/app'

const store = useAppStore()
const weatherText = ref('')
const refundTarget = ref(0)

async function refresh() {
  await Promise.all([store.loadOrders(), store.loadPendingOrders()])
}

async function weather(id: number) {
  weatherText.value = await store.orderWeather(id)
}

async function refund() {
  const id = refundTarget.value
  refundTarget.value = 0
  await store.refundOrder(id)
}

function stationLabel(value: string) {
  return value.replace('的车次', '').replace('车次', '')
}
</script>
