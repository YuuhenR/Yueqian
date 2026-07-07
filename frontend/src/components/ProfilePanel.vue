<template>
  <section class="work-view">
    <div class="page-head">
      <div>
        <h1>账户</h1>
        <p>{{ store.user?.displayName || store.userId }}</p>
      </div>
      <button class="btn primary" type="button" @click="store.init">同步</button>
    </div>

    <div class="profile-grid">
      <section class="profile-block">
        <h2>账号</h2>
        <dl>
          <div>
            <dt>用户名</dt>
            <dd>{{ store.userId }}</dd>
          </div>
          <div>
            <dt>角色</dt>
            <dd>{{ store.user?.role }}</dd>
          </div>
          <div>
            <dt>会话</dt>
            <dd>{{ store.dashboard?.sessionCount ?? 0 }}</dd>
          </div>
        </dl>
      </section>

      <section class="profile-block">
        <h2>业务</h2>
        <dl>
          <div>
            <dt>消息</dt>
            <dd>{{ store.dashboard?.messageCount ?? 0 }}</dd>
          </div>
          <div>
            <dt>出票</dt>
            <dd>{{ store.dashboard?.activeOrderCount ?? 0 }}</dd>
          </div>
          <div>
            <dt>退票</dt>
            <dd>{{ store.dashboard?.refundedOrderCount ?? 0 }}</dd>
          </div>
        </dl>
      </section>
    </div>

    <section v-if="store.user?.role === 'ADMIN'" class="admin-panel">
      <div class="admin-metrics">
        <div><b>{{ store.adminMetrics?.userCount ?? 0 }}</b><span>用户</span></div>
        <div><b>{{ store.adminMetrics?.orderCount ?? 0 }}</b><span>订单</span></div>
        <div><b>{{ store.adminMetrics?.pendingOrderCount ?? 0 }}</b><span>待确认</span></div>
        <div><b>{{ store.adminMetrics?.refundedOrderCount ?? 0 }}</b><span>退票</span></div>
      </div>
      <div class="charts">
        <section>
          <h2>订单趋势</h2>
          <div class="bar-chart">
            <div v-for="item in store.adminMetrics?.dailyOrders || []" :key="item.label">
              <span :style="{ height: `${Math.max(18, item.value / 260)}px` }"></span>
              <small>{{ item.label }}</small>
            </div>
          </div>
        </section>
        <section>
          <h2>热门路线</h2>
          <div class="rank-list">
            <p v-for="item in store.adminMetrics?.routeRanking || []" :key="item.label">
              <strong>{{ item.label }}</strong>
              <span>{{ item.value }}</span>
            </p>
          </div>
        </section>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { useAppStore } from '../stores/app'

const store = useAppStore()
</script>
