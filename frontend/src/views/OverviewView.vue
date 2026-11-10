<template>
  <el-card class="panel" shadow="never">
    <template #header>
      总览
      <span class="muted" style="float: right">按余额倒序，取每个启用账号的最新快照</span>
    </template>
    <div class="toolbar">
      <el-button size="small" :loading="loading" @click="load">刷新</el-button>
      <el-button size="small" type="primary" :loading="collecting" @click="collect">立即采集</el-button>
      <span class="muted" v-if="lastRun">
        最近一次：{{ format(lastRun.startedAt) }} · 共 {{ lastRun.total }} · 成功 {{ lastRun.succeeded }} · 失败 {{ lastRun.failed }}
      </span>
    </div>
    <el-table :data="rows" v-loading="loading" size="small" empty-text="还没有账号：去「采集」页添加">
      <el-table-column prop="accountName" label="账号" min-width="150" />
      <el-table-column prop="platformCode" label="平台" width="150" />
      <el-table-column prop="balance" label="余额" width="120" />
      <el-table-column prop="usedQuota" label="已用" width="120" />
      <el-table-column prop="requestCount" label="请求数" width="100" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.status" size="small" :type="statusType(row.status)">{{ row.status }}</el-tag>
          <span v-else class="muted">未采集</span>
        </template>
      </el-table-column>
      <el-table-column label="采集时间" width="180">
        <template #default="{ row }">{{ format(row.collectedAt) }}</template>
      </el-table-column>
      <el-table-column prop="message" label="说明" min-width="200" show-overflow-tooltip />
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'

const rows = ref<any[]>([])
const runs = ref<any[]>([])
const loading = ref(false)
const collecting = ref(false)

const lastRun = computed(() => (runs.value.length ? runs.value[0] : null))

async function load() {
  loading.value = true
  try {
    rows.value = await api.upstreamDashboard()
    runs.value = await api.collectRuns()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

async function collect() {
  collecting.value = true
  try {
    const summary: any = await api.collectNow()
    ElMessage.success(`采集完成：成功 ${summary.succeeded} / 失败 ${summary.failed}`)
    await load()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    collecting.value = false
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
