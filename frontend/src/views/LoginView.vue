<template>
  <div class="login-shell">
    <el-card class="login-card" shadow="never">
      <div class="brand">
        <strong>RelayOps</strong>
        <small>上游智控台 · 控制台登录</small>
      </div>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名">
          <el-input v-model="username" autocomplete="username" placeholder="admin" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="password" type="password" autocomplete="current-password" show-password
                    @keyup.enter="submit" />
        </el-form-item>
        <el-button type="primary" style="width: 100%" :loading="loading" @click="submit">登录</el-button>
      </el-form>
      <p class="hint">
        账号由后端启动时按 <code>RELAY_OPS_ADMIN_USERNAME</code> / <code>RELAY_OPS_ADMIN_PASSWORD</code> 创建；
        若后端设置了 <code>RELAY_OPS_DEV_OPEN_API=true</code>，可直接跳过登录。
      </p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '../api'

const router = useRouter()
const route = useRoute()
const username = ref('admin')
const password = ref('')
const loading = ref(false)

async function submit() {
  loading.value = true
  try {
    await api.authLogin(username.value, password.value)
    const redirect = (route.query.redirect as string) || '/status'
    await router.replace(redirect)
  } catch (error) {
    ElMessage.error((error as Error).message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-shell {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  background: #1f2d3d;
}
.login-card {
  width: 360px;
}
.brand {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-bottom: 18px;
}
.brand strong {
  font-size: 20px;
}
.brand small {
  color: #8a94a6;
  font-size: 12px;
}
.hint {
  margin-top: 14px;
  color: #8a94a6;
  font-size: 12px;
  line-height: 1.6;
}
</style>
