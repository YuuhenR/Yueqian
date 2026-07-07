<template>
  <div class="app-shell">
    <header class="topbar">
      <RouterLink to="/" class="brand">
        <span class="rail-logo" aria-label="铁路标识">
          <svg viewBox="0 0 64 48" role="img" aria-hidden="true">
            <path d="M8 11h48v7H8z" />
            <path d="M19 19h26v7H19z" />
            <path d="M27 26h10v13H27z" />
            <path d="M7 24h19l-9 15H4z" />
            <path d="M57 24H38l9 15h13z" />
          </svg>
        </span>
        <span>智能铁路票务助手</span>
      </RouterLink>
      <nav class="main-nav" aria-label="主导航">
        <RouterLink to="/" class="nav-link">首页</RouterLink>
        <RouterLink :to="store.user?.role === 'ADMIN' ? '/workspace/profile' : '/workspace/service'" class="nav-link" :class="{ 'router-link-active': route.path.startsWith('/workspace') }">个人中心</RouterLink>
        <template v-if="store.user">
          <span class="user-chip">{{ store.user.displayName || store.user.username }}</span>
          <button class="nav-link nav-button" type="button" @click="logout">退出</button>
        </template>
        <template v-else>
          <RouterLink to="/login" class="nav-link auth-square">登录</RouterLink>
          <RouterLink to="/register" class="nav-link auth-square">注册</RouterLink>
        </template>
      </nav>
    </header>
    <RouterView />
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useRoute } from 'vue-router'
import { useAppStore } from './stores/app'

const store = useAppStore()
const router = useRouter()
const route = useRoute()

async function logout() {
  store.logout()
  await router.push('/')
}
</script>
