<template>
  <main class="workspace-shell">
    <aside class="side-nav">
      <RouterLink v-if="store.user?.role !== 'ADMIN'" to="/workspace/service">办理中心</RouterLink>
      <RouterLink v-if="store.user?.role !== 'ADMIN'" to="/workspace/orders">订单</RouterLink>
      <RouterLink v-if="store.user?.role !== 'ADMIN'" to="/workspace/travel">目的地</RouterLink>
      <RouterLink to="/workspace/profile">账户</RouterLink>
    </aside>

    <section class="workspace-main">
      <ServicePanel v-if="tab === 'service'" />
      <OrdersPanel v-else-if="tab === 'orders'" />
      <TravelPanel v-else-if="tab === 'travel'" />
      <ProfilePanel v-else />
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '../stores/app'
import { getCurrentUser } from '../services/api'
import ServicePanel from '../components/ServicePanel.vue'
import OrdersPanel from '../components/OrdersPanel.vue'
import TravelPanel from '../components/TravelPanel.vue'
import ProfilePanel from '../components/ProfilePanel.vue'

const route = useRoute()
const router = useRouter()
const store = useAppStore()
const tab = computed(() => String(route.params.tab || 'service'))

onMounted(async () => {
  if (!getCurrentUser()) {
    await router.push('/login')
    return
  }
  await store.init()
  if (store.user?.role === 'ADMIN' && tab.value !== 'profile') {
    await router.push('/workspace/profile')
  }
})
</script>
