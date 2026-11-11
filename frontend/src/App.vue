<template>
  <router-view v-if="route.path === '/login'" />
  <el-container v-else class="shell">
    <el-aside width="220px" class="aside">
      <div class="brand">
        <strong>RelayOps</strong>
        <small>上游智控台 · 控制台</small>
      </div>
      <el-menu :default-active="active" router class="menu">
        <el-menu-item index="/status">运行状态</el-menu-item>
        <el-menu-item index="/overview">总览</el-menu-item>
        <el-menu-item index="/monitoring">监控</el-menu-item>
        <el-menu-item index="/chat">对话</el-menu-item>
        <el-menu-item index="/collection">采集</el-menu-item>
        <el-menu-item index="/schedules">定时任务</el-menu-item>
        <el-menu-item index="/personas">人设与提示词</el-menu-item>
        <el-menu-item index="/blueprints">消息蓝图</el-menu-item>
        <el-menu-item index="/memory">记忆</el-menu-item>
        <el-menu-item index="/alerts">告警</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="title">{{ title }}</span>
        <span class="hint">
          <template v-if="user">当前账号：{{ user }} · </template>
          <template v-else>开发模式（未登录） · </template>
          <el-button v-if="user" size="small" text @click="logout">退出</el-button>
        </span>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from './api'

const route = useRoute()
const router = useRouter()
const active = computed(() => route.path)
const title = computed(() => (route.meta.title as string) ?? '')
const user = ref<string | null>(null)

async function refreshUser() {
  try {
    const me: any = await api.authMe()
    user.value = me?.username ?? null
  } catch {
    user.value = null
  }
}

async function logout() {
  try {
    await api.authLogout()
  } finally {
    user.value = null
    await router.replace('/login')
  }
}

onMounted(refreshUser)
</script>

<style>
html,
body,
#app {
  height: 100%;
  margin: 0;
  background: #f5f7fa;
  font-family: 'Segoe UI', 'Microsoft YaHei', sans-serif;
}
.shell {
  height: 100%;
}
.aside {
  background: #1f2d3d;
  color: #d7e1ec;
}
.brand {
  padding: 18px 20px 12px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  color: #fff;
}
.brand small {
  color: #8fa3b8;
  font-size: 12px;
}
.menu {
  border-right: none;
  background: transparent;
}
.menu .el-menu-item {
  color: #c8d4e0;
}
.menu .el-menu-item.is-active {
  color: #fff;
  background: #2f4159;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e5e9f0;
}
.title {
  font-size: 16px;
  font-weight: 600;
}
.hint {
  color: #8a94a6;
  font-size: 12px;
}
.main {
  padding: 20px;
}
.panel {
  margin-bottom: 16px;
}
pre.output {
  background: #0f172a;
  color: #e2e8f0;
  padding: 12px;
  border-radius: 6px;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  line-height: 1.6;
  margin: 0;
}
.toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
</style>
