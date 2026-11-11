<template>
  <el-card class="panel" shadow="never">
    <template #header>
      告警历史
      <el-button size="small" style="float: right" :loading="loading" @click="load">刷新</el-button>
    </template>
    <el-table :data="history" v-loading="loading" size="small" empty-text="还没有发出过告警">
      <el-table-column label="时间" width="170">
        <template #default="{ row }">{{ format(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column prop="accountName" label="账号" min-width="140" />
      <el-table-column label="类型" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="typeTag(row.alertType)">{{ label(row.alertType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="message" label="详情" min-width="240" show-overflow-tooltip />
      <el-table-column prop="channels" label="通道" width="110" />
    </el-table>
  </el-card>

  <el-card shadow="never">
    <template #header>静默规则</template>
    <div class="toolbar">
      <el-select v-model="form.scopeType" style="width: 130px">
        <el-option label="全局" value="GLOBAL" />
        <el-option label="单个账号" value="ACCOUNT" />
      </el-select>
      <el-input v-if="form.scopeType === 'ACCOUNT'" v-model="form.scopeKey" style="width: 130px"
                placeholder="账号 ID" />
      <el-select v-model="form.alertType" style="width: 150px">
        <el-option label="全部类型" value="" />
        <el-option label="余额偏低" value="BALANCE_LOW" />
        <el-option label="采集失败" value="COLLECT_FAILED" />
        <el-option label="采集沉默" value="COLLECT_STALE" />
      </el-select>
      <el-input v-model.number="form.untilHours" style="width: 130px" placeholder="小时数" />
      <el-input v-model="form.reason" style="width: 200px" placeholder="原因（可选）" />
      <el-button type="primary" size="small" :loading="saving" @click="add">添加</el-button>
    </div>
    <el-table :data="rules" size="small" empty-text="没有静默规则">
      <el-table-column label="作用域" width="140">
        <template #default="{ row }">
          {{ row.scopeType === 'GLOBAL' ? '全局' : '账号 ' + row.scopeKey }}
        </template>
      </el-table-column>
      <el-table-column label="类型" width="120">
        <template #default="{ row }">{{ row.alertType ? label(row.alertType) : '全部' }}</template>
      </el-table-column>
      <el-table-column label="到期时间" width="180">
        <template #default="{ row }">{{ format(row.until) }}</template>
      </el-table-column>
      <el-table-column prop="reason" label="原因" min-width="160" />
      <el-table-column label="操作" width="90">
        <template #default="{ row }">
          <el-button size="small" type="danger" plain @click="remove(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-alert type="info" :closable="false" style="margin-top: 10px"
              title="静默只影响发送：告警仍会被判定并写入历史，维护窗口结束后恢复推送。" />
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'

const history = ref<any[]>([])
const rules = ref<any[]>([])
const loading = ref(false)
const saving = ref(false)
const form = reactive({ scopeType: 'GLOBAL', scopeKey: '', alertType: '', untilHours: 24, reason: '' })

async function load() {
  loading.value = true
  try {
    history.value = await api.alertHistory()
    rules.value = await api.silences()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

async function add() {
  saving.value = true
  try {
    await api.addSilence({ ...form })
    ElMessage.success('已添加静默规则')
    await load()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    saving.value = false
  }
}

async function remove(id: number) {
  try {
    await api.deleteSilence(id)
    await load()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

function label(type: string) {
  if (type === 'BALANCE_LOW') return '余额偏低'
  if (type === 'COLLECT_FAILED') return '采集失败'
  if (type === 'COLLECT_STALE') return '采集沉默'
  return type
}

function typeTag(type: string) {
  if (type === 'BALANCE_LOW') return 'warning'
  if (type === 'COLLECT_FAILED') return 'danger'
  return 'info'
}

function format(value?: string) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'
}

onMounted(load)
</script>
