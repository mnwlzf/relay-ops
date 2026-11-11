<template>
  <el-card class="panel" shadow="never">
    <template #header>
      机器人（OneBot / NapCat）
      <el-button size="small" style="float: right" :loading="loading" @click="load">刷新</el-button>
    </template>
    <el-descriptions :column="3" border size="small">
      <el-descriptions-item label="开关">
        <el-tag :type="status?.enabled ? 'success' : 'info'" size="small">
          {{ status?.enabled ? '已启用' : '未启用' }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="可用（配置齐全）">
        <el-tag :type="status?.usable ? 'success' : 'warning'" size="small">
          {{ status?.usable ? '是' : '否' }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="API 地址">{{ status?.apiBaseUrl || '未配置' }}</el-descriptions-item>
      <el-descriptions-item label="上报 token">
        {{ status?.webhookTokenConfigured ? '已配置' : '未配置（接口会返回 401）' }}
      </el-descriptions-item>
      <el-descriptions-item label="API token">
        {{ status?.apiTokenConfigured ? '已配置' : '未配置（NapCat 未开鉴权时可留空）' }}
      </el-descriptions-item>
      <el-descriptions-item label="群消息需要 @">
        {{ status?.mentionRequired ? '是' : '否' }}
      </el-descriptions-item>
      <el-descriptions-item label="白名单群">{{ join(status?.allowedGroups) }}</el-descriptions-item>
      <el-descriptions-item label="管理员 QQ">{{ join(status?.adminUsers) }}</el-descriptions-item>
      <el-descriptions-item label="告警推送群">{{ join(status?.alertGroups) }}</el-descriptions-item>
      <el-descriptions-item label="分片/段数">
        {{ status?.maxMessageChars }} 字 / 最多 {{ status?.maxSegments }} 段
      </el-descriptions-item>
      <el-descriptions-item label="重试">{{ status?.retryMax }} 次</el-descriptions-item>
      <el-descriptions-item label="发送间隔">{{ status?.sendIntervalMillis }} ms</el-descriptions-item>
    </el-descriptions>

    <el-alert v-for="hint in status?.hints ?? []" :key="hint" type="warning" :closable="false"
              :title="hint" style="margin-top: 8px" />
  </el-card>

  <el-card shadow="never">
    <template #header>连通性测试</template>
    <div class="toolbar">
      <el-input v-model="testGroup" style="width: 200px" placeholder="群号（留空用告警推送群）" />
      <el-button type="primary" size="small" :loading="testing" @click="sendTest">发送测试消息</el-button>
    </div>
    <pre v-if="testResult" class="output">{{ testResult }}</pre>
    <el-alert type="info" :closable="false" style="margin-top: 10px"
              title="测试只验证“后端 → NapCat → QQ 群”方向；反过来（群消息 → 后端）需要 NapCat 里配好反向 HTTP 上报。" />
  </el-card>

  <el-card shadow="never">
    <template #header>配置清单（改 .env 后重启后端）</template>
    <el-table :data="checklist" size="small">
      <el-table-column prop="name" label="环境变量" width="320" />
      <el-table-column prop="meaning" label="说明" min-width="300" />
    </el-table>
    <el-alert type="warning" :closable="false" style="margin-top: 10px"
              title="NapCat 侧还要做两件事：WebUI 扫码登录；网络配置里开启 HTTP 服务端(3000) 与反向 HTTP 上报（地址见 README，带 Authorization: Bearer <上报 token>）。" />
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'

const status = ref<any>(null)
const loading = ref(false)
const testing = ref(false)
const testGroup = ref<number | undefined>(undefined)
const testResult = ref('')

const checklist = [
  { name: 'RELAY_OPS_ONEBOT_ENABLED', meaning: '总开关；关闭时接口返回 404、发送只写日志' },
  { name: 'RELAY_OPS_ONEBOT_WEBHOOK_TOKEN', meaning: 'NapCat 回调必须携带的 Bearer token（失败关闭）' },
  { name: 'RELAY_OPS_ONEBOT_API_BASE_URL', meaning: 'NapCat HTTP 服务地址，容器内是 http://napcat:3000' },
  { name: 'RELAY_OPS_ONEBOT_API_TOKEN', meaning: 'NapCat HTTP 服务端开了鉴权时填写' },
  { name: 'RELAY_OPS_ONEBOT_ALLOWED_GROUPS', meaning: '群白名单，留空=任何群都回应' },
  { name: 'RELAY_OPS_ONEBOT_ADMIN_USERS', meaning: '这些 QQ 号在群里可查平台数据' },
  { name: 'RELAY_OPS_ONEBOT_ALERT_GROUPS', meaning: '告警主动推送目标群' },
  { name: 'RELAY_OPS_ONEBOT_REQUIRE_MENTION', meaning: '群里是否必须 @ 机器人才回应' }
]

async function load() {
  loading.value = true
  try {
    status.value = await api.onebotStatus()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

async function sendTest() {
  testing.value = true
  try {
    const result: any = await api.onebotTest(testGroup.value ?? null)
    testResult.value = (result?.submitted ? '已提交：' : '未提交：') + (result?.message ?? '')
    if (!result?.submitted) {
      ElMessage.warning(result?.message ?? '发送失败')
    }
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    testing.value = false
  }
}

function join(values?: number[]) {
  return values && values.length ? values.join(', ') : '未配置'
}

onMounted(load)
</script>
