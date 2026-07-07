<template>
  <main class="auth-page">
    <section class="auth-panel">
      <div class="auth-banner">
        <span>Account</span>
        <strong>登录</strong>
      </div>
      <form class="auth-form" @submit.prevent="submit">
        <div class="role-switch">
          <button type="button" :class="{ active: expectedRole === 'USER' }" @click="expectedRole = 'USER'">用户</button>
          <button type="button" :class="{ active: expectedRole === 'ADMIN' }" @click="expectedRole = 'ADMIN'">管理员</button>
        </div>
        <label>
          账号
          <input v-model="username" autocomplete="username" />
        </label>
        <label>
          密码
          <input v-model="password" type="password" autocomplete="current-password" />
        </label>
        <p v-if="error" class="error">{{ error }}</p>
        <div class="auth-actions">
          <button class="btn primary" type="submit">进入系统</button>
          <RouterLink class="btn auth-mini" to="/register">注册</RouterLink>
        </div>
      </form>
    </section>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAppStore } from '../stores/app'

const store = useAppStore()
const router = useRouter()
const username = ref('')
const password = ref('')
const error = ref('')
const expectedRole = ref<'USER' | 'ADMIN'>('USER')

async function submit() {
  try {
    error.value = ''
    await store.login(username.value, password.value, expectedRole.value)
    await router.push(expectedRole.value === 'ADMIN' ? '/workspace/profile' : '/workspace/service')
  } catch (err) {
    error.value = err instanceof Error ? err.message : '登录失败'
  }
}
</script>
