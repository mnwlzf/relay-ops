<template>
  <el-card class="panel" shadow="never">
    <template #header>
      采集
      <el-button size="small" style="float: right" :loading="loading" @click="load">刷新</el-button>
    </template>
    <div class="toolbar">
      <el-select v-model="platformCode" placeholder="平台" style="width: 230px">
        <el-option v-for="item in platforms" :key="item.code"
                   :label="`${item.name}（${item.type}）${item.enabled ? '' : ' · 停用'}`"
                   :value="item.code" />
      </el-select>
      <el-input v-model="name" placeholder="账号名称" style="width: 150px" />
      <el-input v-model="login" placeholder="登录名 / 用户 ID" style="width: 170px" />
      <el-input v-model="credential" placeholder="凭证（Token，必填，加密存储）" style="width: 240px" show-password />
      <el-button :loading="saving" @click="createAccount">添加账号</el-button>
      <el-button type="primary" :loading="collecting" @click="collect">立即采集</el-button>
    </div>
    <div class="toolbar">
      <span class="muted">新增平台：</span>
      <el-input v-model="platformForm.code" placeholder="编码，如 my-newapi" style="width: 180px" />
      <el-input v-model="platformForm.name" placeholder="名称" style="width: 150px" />
      <el-select v-model="platformForm.type" style="width: 140px">
        <el-option label="New API" value="NEWAPI" />
        <el-option label="Sub2API" value="SUB2API" />
      </el-select>
      <el-input v-model="platformForm.baseUrl" placeholder="基础地址，如 https://api.example.com" style="width: 280px" />
      <el-button :loading="savingPlatform" @click="createPlatform">保存平台</el-button>
    </div>
    <el-alert v-if="!platforms.length" type="info" :closable="false" show-icon
              title="还没有平台：用上面这行新增一个（New API / Sub2API），或启用迁移里种的演示平台。" />
    <el-table :data="accounts" v-loading="loading" size="small" style="margin-top: 12px"
              empty-text="还没有账号">
      <el-table-column prop="name" label="账号" min-width="140" />
      <el-table-column prop="platformCode" label="平台" width="150" />
      <el-table-column prop="login" label="登录名" width="160" show-overflow-tooltip />
      <el-table-column prop="credentialHint" label="凭证" width="120" />
      <el-table-column label="最近采集" width="210">
        <template #default="{ row }">
          <el-tag v-if="row.lastCollectStatus" size="small" :type="statusType(row.lastCollectStatus)">
            {{ row.lastCollectStatus }}
          </el-tag>
          <span class="muted"> {{ format(row.lastCollectAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="lastCollectMessage" label="说明" min-width="220" show-overflow-tooltip />
      <el-table-column label="操作" width="110">
        <template #default="{ row }">
          <el-button size="small" @click="showSnapshots(row)">快照</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>

  <el-card shadow="never">
    <template #header>最近采集运行</template>
    <el-table :data="runs" size="small" empty-text="还没有运行记录">
      <el-table-column label="开始" width="180">
        <template #default="{ row }">{{ format(row.startedAt) }}</template>
      </el-table-column>
      <el-table-column prop="triggerSource" label="来源" width="100" />
      <el-table-column prop="total" label="账号" width="70" />
      <el-table-column prop="succeeded" label="成功" width="70" />
      <el-table-column prop="failed" label="失败" width="70" />
      <el-table-column label="结束" width="180">
        <template #default="{ row }">{{ format(row.finishedAt) }}</template>
      </el-table-column>
    </el-table>
  </el-card>

  <el-drawer v-model="drawer" :title="`快照 · ${currentAccount?.name ?? ''}`" size="620px">
    <el-table :data="snapshots" size="small" empty-text="暂无快照">
      <el-table-column label="时间" width="170">
        <template #default="{ row }">{{ format(row.collectedAt) }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="90" />
      <el-table-column prop="balance" label="余额" width="100" />
      <el-table-column prop="usedQuota" label="已用" width="100" />
      <el-table-column prop="requestCount" label="请求数" width="90" />
      <el-table-column prop="message" label="说明" min-width="150" show-overflow-tooltip />
    </el-table>
  </el-drawer>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'

const platforms = ref<any[]>([])
const accounts = ref<any[]>([])
const runs = ref<any[]>([])
const snapshots = ref<any[]>([])
const currentAccount = ref<any>(null)
const platformCode = ref('')
const name = ref('主账号')
const login = ref('')
const credential = ref('')
const loading = ref(false)
const saving = ref(false)
const collecting = ref(false)
const drawer = ref(false)
const savingPlatform = ref(false)
const platformForm = reactive({ code: '', name: '', type: 'NEWAPI', baseUrl: '' })

async function createPlatform() {
  if (!platformForm.code.trim() || !platformForm.name.trim() || !platformForm.baseUrl.trim()) {
    ElMessage.warning('平台编码、名称、基础地址都要填')
    return
  }
  savingPlatform.value = true
  try {
    await api.createPlatform({ ...platformForm })
    ElMessage.success('平台已添加')
    platformForm.code = ''
    platformForm.name = ''
    platformForm.baseUrl = ''
    await load()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    savingPlatform.value = false
  }
}

async function load() {
  loading.value = true
  try {
    platforms.value = await api.platforms()
    accounts.value = await api.upstreamAccounts()
    runs.value = await api.collectRuns()
    if (!platformCode.value && platforms.value.length) {
      platformCode.value = platforms.value[0].code
    }
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

async function createAccount() {
  // 先在前端把空值挡住：后端只会回一句"凭证不能为空"，看不出是哪个框没填
  if (!platformCode.value) {
    ElMessage.warning('请先选择平台')
    return
  }
  if (!name.value.trim()) {
    ElMessage.warning('请填写账号名称')
    return
  }
  if (!credential.value.trim()) {
    ElMessage.warning('请填写凭证（Token）—— 这个字段是必填的')
    return
  }
  saving.value = true
  try {
    await api.createUpstreamAccount({
      platformCode: platformCode.value,
      name: name.value,
      login: login.value,
      credential: credential.value
    })
    ElMessage.success('账号已添加（凭证已加密）')
    credential.value = ''
    await load()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    saving.value = false
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

async function showSnapshots(row: any) {
  currentAccount.value = row
  try {
    snapshots.value = await api.snapshots(row.id)
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
