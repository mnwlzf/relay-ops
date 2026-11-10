<template>
  <el-card class="panel" shadow="never">
    <template #header>
      人设列表
      <el-button size="small" style="float: right" :loading="loading" @click="load">刷新</el-button>
    </template>
    <el-table :data="personas" v-loading="loading" size="small" @row-click="pick">
      <el-table-column prop="name" label="名称" min-width="140" />
      <el-table-column prop="code" label="code" width="140" />
      <el-table-column label="适用包" min-width="160">
        <template #default="{ row }">
          <el-tag v-for="pack in row.packs" :key="pack" size="small" style="margin-right: 4px">{{ pack }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="默认" width="70">
        <template #default="{ row }">
          <el-tag v-if="row.defaultPersona" type="success" size="small">默认</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="version" label="版本" width="70" />
    </el-table>
  </el-card>

  <el-card shadow="never">
    <template #header>提示词预览（模型真正收到的文本）</template>
    <div class="toolbar">
      <el-select v-model="selectedId" placeholder="选择人设" style="width: 220px">
        <el-option v-for="item in personas" :key="item.id" :label="item.name" :value="item.id" />
      </el-select>
      <el-select v-model="packId" style="width: 160px">
        <el-option label="general" value="general" />
        <el-option label="platform" value="platform" />
      </el-select>
      <el-checkbox v-model="admin">管理员</el-checkbox>
      <el-button type="primary" :loading="previewing" @click="preview">生成预览</el-button>
      <span class="muted">预算 {{ budget }} 字符</span>
    </div>
    <pre v-if="rendered" class="output">{{ rendered }}</pre>
    <el-empty v-else description="选择人设后点「生成预览」" />
    <el-table v-if="sections.length" :data="sections" size="small" style="margin-top: 12px">
      <el-table-column prop="name" label="段落" width="140" />
      <el-table-column prop="layer" label="层级" width="120" />
      <el-table-column label="可覆盖" width="90">
        <template #default="{ row }">{{ row.overridable ? '是' : '受保护' }}</template>
      </el-table-column>
      <el-table-column prop="length" label="长度" width="90" />
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'

const personas = ref<any[]>([])
const selectedId = ref<number | null>(null)
const packId = ref('general')
const admin = ref(false)
const rendered = ref('')
const sections = ref<any[]>([])
const budget = ref(0)
const loading = ref(false)
const previewing = ref(false)

async function load() {
  loading.value = true
  try {
    personas.value = await api.personas()
    if (!selectedId.value && personas.value.length) {
      selectedId.value = personas.value[0].id
    }
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

function pick(row: any) {
  selectedId.value = row.id
}

async function preview() {
  previewing.value = true
  try {
    const data: any = await api.promptPreview({
      packId: packId.value,
      personaId: selectedId.value,
      scopeType: 'GLOBAL',
      scopeKey: '*',
      admin: admin.value,
      platformUser: admin.value
    })
    rendered.value = data.rendered
    sections.value = data.sections ?? []
    budget.value = data.budget
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
