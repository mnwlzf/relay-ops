<template>
  <el-card shadow="never">
    <template #header>
      定时任务
      <el-button size="small" style="float: right" :loading="loading" @click="load">刷新</el-button>
    </template>
    <el-table :data="tasks" v-loading="loading" size="small" empty-text="暂无任务">
      <el-table-column prop="name" label="名称" min-width="160" />
      <el-table-column prop="taskType" label="类型" width="170" />
      <el-table-column prop="kind" label="调度" width="150" />
      <el-table-column label="启用" width="80">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="下次触发" width="180">
        <template #default="{ row }">{{ format(row.nextFireAt) }}</template>
      </el-table-column>
      <el-table-column label="最近结果" width="120">
        <template #default="{ row }">
          <el-tag v-if="row.lastRunStatus" size="small" :type="statusType(row.lastRunStatus)">
            {{ row.lastRunStatus }}
          </el-tag>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="toggle(row)">{{ row.enabled ? '停用' : '启用' }}</el-button>
          <el-button size="small" :disabled="!row.enabled" @click="run(row)">立即执行</el-button>
          <el-button size="small" @click="preview(row)">未来 10 次</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-drawer v-model="drawer" title="未来 10 次触发" size="420px">
      <p v-if="previewTask" class="muted">{{ previewTask.name }} · 按当前 spec 与工作日历计算</p>
      <el-timeline>
        <el-timeline-item v-for="item in previews" :key="item.fireAt" :timestamp="item.localDateTime">
          <el-tag size="small" :type="item.dayType === 'WORKDAY' ? 'success' : 'info'">{{ item.dayType }}</el-tag>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-if="!previews.length" description="没有可计算的触发时间" />
    </el-drawer>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'

const tasks = ref<any[]>([])
const previews = ref<any[]>([])
const previewTask = ref<any>(null)
const loading = ref(false)
const drawer = ref(false)

async function load() {
  loading.value = true
  try {
    tasks.value = await api.tasks()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

async function toggle(row: any) {
  try {
    await api.toggleTask(row.id, !row.enabled)
    ElMessage.success(row.enabled ? '已停用' : '已启用')
    await load()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function run(row: any) {
  try {
    await api.runTask(row.id)
    ElMessage.success('已触达，稍后看「运行状态」的最近播报')
    setTimeout(load, 800)
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function preview(row: any) {
  try {
    previewTask.value = row
    previews.value = await api.previewSpec(row.spec, 10)
    drawer.value = true
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

function format(value?: string) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'
}

function statusType(status: string) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED' || status === 'TIMEOUT') return 'danger'
  if (status === 'SKIPPED') return 'warning'
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
