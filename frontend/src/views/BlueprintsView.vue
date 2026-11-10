<template>
  <el-card class="panel" shadow="never">
    <template #header>
      消息蓝图
      <el-button size="small" style="float: right" :loading="loading" @click="load">刷新</el-button>
    </template>
    <el-table :data="blueprints" v-loading="loading" size="small" highlight-current-row @current-change="pick">
      <el-table-column prop="code" label="code" width="180" />
      <el-table-column prop="name" label="名称" min-width="160" />
      <el-table-column prop="packId" label="能力包" width="110" />
      <el-table-column label="段" width="70">
        <template #default="{ row }">{{ row.segments?.length ?? 0 }}</template>
      </el-table-column>
      <el-table-column label="启用" width="80">
        <template #default="{ row }">
          <el-tag size="small" :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
    </el-table>
  </el-card>

  <el-card v-if="current" shadow="never">
    <template #header>dry-run 预览 · {{ current.name }}</template>
    <div class="toolbar">
      <el-input v-model="memo" placeholder="args.memo（喂给 {{args.memo}} 占位符）" style="width: 320px" />
      <el-button type="primary" :loading="previewing" @click="preview">生成预览（不投递）</el-button>
      <span class="muted">
        工具调用 {{ result?.toolCalls ?? 0 }} 次
        <el-tag v-if="result?.degraded" type="warning" size="small" style="margin-left: 6px">有降级</el-tag>
      </span>
    </div>
    <pre v-if="result" class="output">{{ result.text }}</pre>
    <el-table v-if="result" :data="result.outcomes" size="small" style="margin-top: 12px">
      <el-table-column prop="index" label="#" width="50" />
      <el-table-column prop="type" label="段类型" width="110" />
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column prop="detail" label="说明" min-width="240" show-overflow-tooltip />
    </el-table>
    <el-collapse style="margin-top: 12px">
      <el-collapse-item title="查看段定义（JSON）" name="json">
        <pre class="output">{{ JSON.stringify(current.segments, null, 2) }}</pre>
      </el-collapse-item>
    </el-collapse>
    <el-empty v-if="!result" description="点「生成预览」看渲染效果" />
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'

const blueprints = ref<any[]>([])
const current = ref<any>(null)
const result = ref<any>(null)
const memo = ref('今天的号池一切正常')
const loading = ref(false)
const previewing = ref(false)

async function load() {
  loading.value = true
  try {
    blueprints.value = await api.blueprints()
    if (!current.value && blueprints.value.length) {
      current.value = blueprints.value[0]
    }
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

function pick(row: any) {
  if (!row) return
  current.value = row
  result.value = null
}

async function preview() {
  previewing.value = true
  try {
    result.value = await api.blueprintPreview(current.value.id, {
      args: { memo: memo.value },
      scopeType: 'GLOBAL',
      scopeKey: '*'
    })
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    previewing.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.muted {
  color: #8a94a6;
  font-size: 12px;
}
</style>
