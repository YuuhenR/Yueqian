<template>
  <section class="work-view">
    <div class="page-head">
      <div>
        <h1>目的地</h1>
        <p>输入偏好，生成一条出行建议。</p>
      </div>
    </div>

    <div class="travel-layout">
      <section class="draw-stage" :class="{ spinning }">
        <div class="draw-name">{{ destinationName }}</div>
        <div class="draw-sub">{{ spinning ? 'AI 正在生成' : '铁路出行建议' }}</div>
        <span class="runner"></span>
      </section>

      <aside class="destination-list ai-destination">
        <textarea v-model="preference" placeholder="例如：从广州出发，想去凉快一点的地方" />
        <button class="btn primary" type="button" @click="generate">生成建议</button>
        <div class="ai-suggestion">
          <strong>建议</strong>
          <p>{{ suggestion || '输入偏好后生成。' }}</p>
        </div>
      </aside>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useAppStore } from '../stores/app'

const store = useAppStore()
const spinning = ref(false)
const preference = ref('')
const suggestion = ref('')

const destinationName = computed(() => {
  const text = suggestion.value
  const matched = text.match(/(?:去|目的地[：:])([\u4e00-\u9fa5]{2,6})/)
  return matched?.[1] || (suggestion.value ? 'AI 建议' : '下一站')
})

async function generate() {
  spinning.value = true
  const text = preference.value.trim() || '帮我推荐一个适合高铁出行的目的地'
  const before = store.messages.length
  await store.sendMessage(`${text}，请给出一个简短目的地建议`)
  const latest = store.messages.slice(before).reverse().find(item => item.role === 'assistant')
  suggestion.value = latest?.content || '暂未生成建议。'
  spinning.value = false
}
</script>
