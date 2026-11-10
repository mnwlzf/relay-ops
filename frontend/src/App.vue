<template>
  <el-container class="shell">
    <el-aside width="220px" class="aside">
      <div class="brand">
        <strong>RelayOps</strong>
        <small>上游智控台 · 控制台</small>
      </div>
      <el-menu :default-active="active" router class="menu">
        <el-menu-item index="/status">运行状态</el-menu-item>
        <el-menu-item index="/chat">对话</el-menu-item>
        <el-menu-item index="/schedules">定时任务</el-menu-item>
        <el-menu-item index="/personas">人设与提示词</el-menu-item>
        <el-menu-item index="/blueprints">消息蓝图</el-menu-item>
        <el-menu-item index="/memory">记忆</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="title">{{ title }}</span>
        <span class="hint">本地开发模式：后端需设置 RELAY_OPS_DEV_OPEN_API=true</span>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const active = computed(() => route.path)
const title = computed(() => (route.meta.title as string) ?? '')
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
