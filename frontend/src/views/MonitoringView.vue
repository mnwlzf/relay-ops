<template>
  <el-card class="panel" shadow="never">
    <template #header>
      监控总览
      <span class="muted" style="float: right">
        合计余额 {{ totalBalance }} · 今日消耗 {{ totalToday }}
      </span>
    </template>
    <div class="toolbar">
      <el-button size="small" :loading="loading" @click="load">刷新</el-button>
      <el-tag v-if="abnormal.length" type="warning" size="small">{{ abnormal.length }} 个账号需要关注</el-tag>
      <el-tag v-else type="success" size="small">全部正常</el-tag>
    </div>
    <el-table :data="accounts" v-loading="loading" size="small" empty-text="还没有采集数据">
      <el-table-column prop="accountName" label="账号" min-width="150" />
      <el-table-column prop="platformCode" label="平台" width="140" />
      <el-table-column prop="balance" label="余额" width="110" />
      <el-table-column prop="usedQuota" label="已用" width="110" />
      <el-table-column prop="todayConsumption" label="今日消耗" width="110" />
      <el-table-column prop="requestCount" label="请求数" width="100" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.status" size="small" :type="statusType(row.status)">{{ row.status }}</el-tag>
          <span v-else class="muted">未采集</span>
        </template>
      </el-table-column>
      <el-table-column label="采集时间" width="170">
        <template #default="{ row }">{{ format(row.collectedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90">
        <template #default="{ row }">
          <el-button size="small" @click="showTrend(row)">趋势</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-alert v-if="accounts.length && totalToday === '未知'" type="info" :closable="false" style="margin-top: 10px"
              title="今日消耗未知：当天只有一条快照，或上游重置过计数器（负差值会被忽略，不会算成消耗）。" />
  </el-card>

  <el-card shadow="never">
    <template #header>异常账号</template>
    <el-table :data="abnormal" size="small" empty-text="没有异常账号">
      <el-table-column prop="accountName" label="账号" min-width="150" />
      <el-table-column prop="platformCode" label="平台" width="140" />
      <el-table-column prop="reason" label="原因" width="150" />
      <el-table-column label="最近采集" width="170">
        <template #default="{ row }">{{ format(row.collectedAt) }}</template>
      </el-table-column>
      <el-table-column prop="message" label="说明" min-width="220" show-overflow-tooltip />
    </el-table>
  </el-card>

  <el-drawer v-model="drawer" :title="`趋势 · ${currentAccount?.accountName ?? ''}`" size="640px">
    <div class="toolbar">
      <el-select v-model="hours" style="width: 130px" @change="loadTrend">
        <el-option label="近 6 小时" :value="6" />
        <el-option label="近 24 小时" :value="24" />
        <el-option label="近 7 天" :value="168" />
      </el-select>
    </div>
    <el-table :data="trend" size="small" empty-text="窗口内没有快照">
      <el-table-column label="时间" width="170">
        <template #default="{ row }">{{ format(row.collectedAt) }}</template>
      </el-table-column>
      <el-table-column prop="balance" label="余额" width="100" />
      <el-table-column prop="usedQuota" label="已用" width="100" />
      <el-table-column prop="requestCount" label="请求数" width="90" />
      <el-table-column prop="status" label="状态" width="100" />
    </el-table>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'

const accounts = ref<any[]>([])
const abnormal = ref<any[]>([])
const trend = ref<any[]>([])
const currentAccount = ref<any>(null)
const loading = ref(false)
const drawer = ref(false)
const hours = ref(24)

const totalBalance = computed(() => sum(accounts.value.map((row) => row.balance)))
const totalToday = computed(() => sum(accounts.value.map((row) => row.todayConsumption)))

function sum(values: Array<string | number | null>) {
  const numbers = values.filter((value) => value !== null && value !== undefined && value !== '')
  if (!numbers.length) return '未知'
  return numbers.reduce((acc: number, value) => acc + Number(value), 0).toFixed(2)
}

async function load() {
  loading.value = true
  try {
    const overview: any = await api.monitoringOverview()
    accounts.value = overview.accounts ?? []
    abnormal.value = await api.monitoringAbnormal()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

async function showTrend(row: any) {
  currentAccount.value = row
  drawer.value = true
  await loadTrend()
}

async function loadTrend() {
  try {
    trend.value = await api.monitoringTrend(currentAccount.value.accountId, hours.value)
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

function format(value?: string) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'
}

function statusType(status: string) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'PARTIAL') return 'warning'
  if (status === 'FAILED') return 'danger'
  return 'info'
}

onMounted(load)
</script>

<style scoped>
.muted {
  color: #8a94a6;
  font-size: 12px;
}
</style>
