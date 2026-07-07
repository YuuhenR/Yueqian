<template>
  <main class="home-page">
    <section class="hero-card">
      <div class="hero-copy">
        <p class="kicker">Railway Service</p>
        <h1>智能铁路票务中心</h1>
        <p class="lead">{{ welcomeText }}</p>
        <div class="hero-actions">
          <RouterLink to="/workspace/service" class="btn primary">进入系统</RouterLink>
          <RouterLink v-if="currentUser" to="/workspace/profile" class="btn">个人中心</RouterLink>
          <RouterLink v-else to="/login" class="btn">登录账号</RouterLink>
        </div>
      </div>
      <div class="hero-board">
        <RouterLink to="/workspace/service" class="board-row board-large">
          <span>01 / Ticket</span>
          <strong>车票办理</strong>
          <small>AI 助手生成待确认订单</small>
        </RouterLink>
        <div class="board-grid">
          <RouterLink to="/workspace/orders" class="board-row yellow">
            <span>02</span>
            <strong>订单</strong>
          </RouterLink>
          <RouterLink to="/workspace/travel" class="board-row green">
            <span>03</span>
            <strong>目的地</strong>
          </RouterLink>
        </div>
        <RouterLink to="/workspace/profile" class="board-row board-strip">
          <span>04 / Account</span>
          <strong>个人中心</strong>
        </RouterLink>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useAppStore } from '../stores/app'
import { getCurrentUser } from '../services/api'

const store = useAppStore()
const currentUser = computed(() => store.user || getCurrentUser())

const welcomeText = computed(() => {
  if (!currentUser.value) return '\u8d2d\u7968\u3001\u9000\u7968\u3001\u8ba2\u5355\u7ba1\u7406\u4e0e\u76ee\u7684\u5730\u63a8\u8350\u3002'
  return '\u4f60\u597d\uff0c' + (currentUser.value.displayName || currentUser.value.username)
})
</script>
