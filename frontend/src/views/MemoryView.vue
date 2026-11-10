<template>
  <el-card class="panel" shadow="never">
    <template #header>
      记忆事实
      <el-button size="small" style="float: right" :loading="loading" @click="load">刷新</el-button>
    </template>
    <div class="toolbar">
      <el-select v-model="namespace" style="width: 140px" @change="load">
        <el-option label="GENERAL" value="GENERAL" />
        <el-option label="PLATFORM" value="PLATFORM" />
        <el-option label="SHARED" value="SHARED" />
      </el-select>
      <el-input v-model="scopeKey" placeholder="作用域键，例如 qq:group:100" style="width: 240px" />
      <el-input v-model="factKey" placeholder="key（可选）" style="width: 140px" />
      <el-input v-model="factValue" placeholder="要记住的内容" style="width: 320px" />
      <el-button type="primary" :loading="saving" @click="remember">写入</el-button>
    </div>
    <el-table :data="facts" v-loading="loading" size="small" empty-text="还没有记忆">
      <el-table-column prop="category" label="分类" width="120" />
      <el-table-column prop="factKey" label="key" width="140" />
      <el-table-column prop="value" label="内容" min-width="260" show-overflow-tooltip />
      <el-table-column prop="source" label="来源" width="120" />
      <el-table-column prop="confidence" label="置信度" width="90" />
      <el-table-column label="创建" width="170">
        <template #default="{ row }">{{ format(row.createdAt) }}</template>
      </el-table-column>
    </el-table>
  </el-card>

  <el-card shadow="never">
    <template #header>召回预览（混合检索）</template>
    <div class="toolbar">
      <el-input v-model="query" placeholder="例如：缓存率 / 回答风格" style="width: 320px" />
      <el-button :loading="recalling" @click="recall">召回</el-button>
      <span class="muted">{{ mode ? '模式：' + mode : '' }}</span>
    </div>
    <el-empty v-if="!recalled.length" description="输入问题后点「召回」" />
    <el-table v-else :data="recalled" size="small">
      <el-table-column prop="namespace" label="命名空间" width="120" />
      <el-table-column prop="factKey" label="key" width="140" />
      <el-table-column prop="value" label="内容" min-width="260" show-overflow-tooltip />
      <el-table-column prop="confidence" label="置信度" width="90" />
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'

const namespace = ref('GENERAL')
const scopeType = ref('GROUP')
const scopeKey = ref('qq:group:100')
const factKey = ref('')
const factValue = ref('')
const query = ref('缓存率')
const facts = ref<any[]>([])
const recalled = ref<any[]>([])
const mode = ref('')
const loading = ref(false)
const saving = ref(false)
const recalling = ref(false)

async function load() {
  loading.value = true
  try {
    facts.value = await api.memoryFacts({
      namespace: namespace.value,
      scopeType: scopeType.value,
      scopeKey: scopeKey.value,
      limit: 50
    })
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

async function remember() {
  saving.value = true
  try {
    await api.remember({
      namespace: namespace.value,
      scopeType: scopeType.value,
      scopeKey: scopeKey.value,
      category: 'preference',
      key: factKey.value || null,
      value: factValue.value,
      source: 'EXPLICIT'
    })
    ElMessage.success('已写入')
    factValue.value = ''
    await load()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    saving.value = false
  }
}

async function recall() {
  recalling.value = true
  try {
    const data: any = await api.recall({
      namespace: namespace.value,
      scopeType: scopeType.value,
      scopeKey: scopeKey.value,
      query: query.value,
      limit: 5
    })
    recalled.value = data.facts ?? []
    mode.value = data.mode
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    recalling.value = false
  }
}

function format(value?: string) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'
}

onMounted(load)
</script>

<style scoped>
.muted {
  color: #8a94a6;
  font-size: 12px;
}
</style>
