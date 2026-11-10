<template>
  <el-card class="panel" shadow="never">
    <template #header>模型状态</template>
    <el-alert v-if="status" :type="status.available ? 'success' : 'warning'" :closable="false"
              :title="status.reason" show-icon />
    <el-table v-if="status" :data="status.profiles" size="small" style="margin-top: 12px">
      <el-table-column prop="id" label="槽位" width="100" />
      <el-table-column prop="model" label="模型" min-width="160" />
      <el-table-column prop="baseUrl" label="端点" min-width="220" show-overflow-tooltip />
      <el-table-column prop="maskedApiKey" label="密钥" width="150" />
    </el-table>
  </el-card>

  <el-card class="panel" shadow="never">
    <template #header>
      最近一次播报
      <el-button size="small" style="float: right" @click="load">刷新</el-button>
    </template>
    <el-empty v-if="!last" description="还没有投递过消息：去「消息蓝图」点一次预览或「定时任务」立即执行" />
    <template v-else>
      <p class="meta">{{ last.blueprintCode }} · {{ last.scope.type }}:{{ last.scope.key }} · {{ last.at }}</p>
      <pre class="output">{{ last.text }}</pre>
    </template>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'

const status = ref<any>(null)
const last = ref<any>(null)

async function load() {
  try {
    status.value = await api.modelStatus()
    last.value = await api.lastMessage()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

onMounted(load)
</script>

<style scoped>
.meta {
  color: #6b7785;
  font-size: 12px;
  margin: 0 0 8px;
}
</style>
