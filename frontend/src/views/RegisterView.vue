<template>
  <main class="auth-page">
    <section class="auth-panel register-panel">
      <div class="auth-banner">
        <span>Account</span>
        <strong>注册</strong>
      </div>
      <form class="auth-form" @submit.prevent="submit">
        <label>
          账号
          <input v-model="username" autocomplete="username" />
        </label>
        <label>
          昵称
          <input v-model="displayName" autocomplete="name" />
        </label>
        <label>
          密码
          <input v-model="password" type="password" autocomplete="new-password" />
        </label>
        <p v-if="error" class="error">{{ error }}</p>
        <div class="auth-actions">
          <button class="btn primary" type="submit">创建账号</button>
          <RouterLink class="btn auth-mini" to="/login">登录</RouterLink>
        </div>
      </form>
    </section>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { register } from '../services/api'

const router = useRouter()
const username = ref('')
const displayName = ref('')
const password = ref('')
const error = ref('')

async function submit() {
  try {
    error.value = ''
    await register(username.value, displayName.value, password.value)
    await router.push('/login')
  } catch (err) {
    error.value = err instanceof Error ? err.message : '注册失败'
  }
}
</script>
