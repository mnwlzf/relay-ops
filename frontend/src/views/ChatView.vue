<template>
  <el-card class="panel" shadow="never">
    <template #header>
      对话（Web 入口）
      <span class="muted" style="float: right">
        人设 {{ personaHint }} · 记忆召回 {{ last?.memoryHits ?? 0 }} 条
      </span>
    </template>
    <div class="toolbar">
      <el-select v-model="packId" style="width: 150px">
        <el-option label="general" value="general" />
        <el-option label="platform" value="platform" />
      </el-select>
      <el-input v-model="text" placeholder="说点什么…" style="flex: 1; min-width: 260px"
                @keyup.enter="send" />
      <el-button type="primary" :loading="sending" @click="send">发送</el-button>
    </div>
    <el-alert v-if="last && last.mode === 'UNAVAILABLE'" type="warning" :closable="false" show-icon
              title="未配置聊天模型：本轮只记录消息，回复是固定提示。配置 .env 里的 RELAY_OPS_AI_CHAT_* 后即可对话。" />
    <div v-if="turns.length" class="turns">
      <div v-for="(turn, index) in turns" :key="index" class="turn">
        <div class="who">我</div>
        <div class="text">{{ turn.question }}</div>
        <div class="who assistant">助手（{{ turn.mode }}）</div>
        <div class="text assistant-text">{{ turn.reply }}</div>
        <div v-if="turn.memoryKeys?.length" class="muted">命中记忆：{{ turn.memoryKeys.join('、') }}</div>
      </div>
    </div>
    <el-empty v-else description="发一条消息，看看提示词装配 + 记忆召回 + 模型路由是否打通" />
  </el-card>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'

const text = ref('你好，介绍一下你能做什么')
const packId = ref('general')
const sending = ref(false)
const last = ref<any>(null)
const turns = ref<any[]>([])
const personaHint = ref('按作用域解析')

async function send() {
  if (!text.value.trim()) return
  sending.value = true
  const question = text.value
  try {
    const reply: any = await api.chat({
      text: question,
      packId: packId.value,
      scopeType: 'USER',
      scopeKey: 'qq:user:1',
      userKey: 'qq:user:1',
      speakerName: '控制台',
      admin: true,
      platformUser: true
    })
    last.value = reply
    turns.value.unshift({ question, reply: reply.reply, mode: reply.mode, memoryKeys: reply.memoryKeys })
    text.value = ''
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    sending.value = false
  }
}
</script>

<style scoped>
.turns {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.turn {
  border: 1px solid #e5e9f0;
  border-radius: 8px;
  padding: 12px;
  background: #fff;
}
.who {
  font-size: 12px;
  color: #8a94a6;
  margin-bottom: 2px;
}
.who.assistant {
  margin-top: 10px;
}
.text {
  white-space: pre-wrap;
  word-break: break-word;
}
.assistant-text {
  color: #1f2d3d;
}
.muted {
  color: #8a94a6;
  font-size: 12px;
  margin-top: 6px;
}
</style>
