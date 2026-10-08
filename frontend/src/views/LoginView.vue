<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { authApi } from '@/api'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const loginError = ref('')
const rememberStorageVersion = '2'
if (localStorage.getItem('rememberedUserVersion') !== rememberStorageVersion) {
  localStorage.removeItem('rememberedUser')
  localStorage.setItem('rememberedUserVersion', rememberStorageVersion)
}
const form = reactive({
  username: localStorage.getItem('rememberedUser') || '',
  password: '',
  captcha: '',
  captchaId: '',
  remember: true
})
const captchaEnabled = ref(false)
const captchaImage = ref('')
const loadCaptcha = async () => {
  const response = await authApi.captcha()
  captchaEnabled.value = response.data.enabled
  captchaImage.value = response.data.image || ''
  form.captchaId = response.data.captchaId || ''
  form.captcha = ''
}

const submit = async () => {
  if (loading.value) return
  loginError.value = ''
  loading.value = true
  try {
    await auth.login(form)
    if (form.remember) localStorage.setItem('rememberedUser', form.username)
    else localStorage.removeItem('rememberedUser')
    router.replace(auth.isAdmin ? '/system/admin' : '/dashboard')
  } catch (error: unknown) {
    loginError.value = loginFailureMessage(error)
    try {
      await loadCaptcha()
    } catch {
      // 保留原始登录错误，验证码刷新失败不应覆盖登录反馈。
    }
  } finally {
    loading.value = false
  }
}

const loginFailureMessage = (error: unknown) => {
  if (typeof error !== 'object' || error === null) return '登录失败，请检查账号和密码'
  const candidate = error as { response?: { data?: { message?: unknown } }; message?: unknown }
  const message = candidate.response?.data?.message || candidate.message
  return typeof message === 'string' ? message : '登录失败，请检查账号和密码'
}
onMounted(loadCaptcha)
</script>

<template>
  <div class="login-page">
    <div class="rail-lines"></div>
    <div class="login-heading">
      <div class="police-emblem">警</div>
      <div>
        <h1>铁路公安内部治安保卫</h1>
        <h2>监督检查管理系统</h2>
        <p>RAILWAY PUBLIC SECURITY SUPERVISION SYSTEM</p>
      </div>
    </div>
    <el-card class="login-card" shadow="always">
      <div class="login-card-title"><b>用户登录</b><span>公安内网业务系统</span></div>
      <el-form @keyup.enter="submit">
        <el-alert v-if="loginError" class="login-error" type="error" show-icon :closable="false" :title="loginError" />
        <el-form-item
          ><el-input v-model="form.username" size="large" placeholder="请输入账号" prefix-icon="User"
        /></el-form-item>
        <el-form-item
          ><el-input
            v-model="form.password"
            size="large"
            type="password"
            show-password
            placeholder="请输入密码"
            prefix-icon="Lock"
        /></el-form-item>
        <el-form-item v-if="captchaEnabled">
          <el-input v-model="form.captcha" size="large" placeholder="请输入验证码" prefix-icon="Key">
            <template #append
              ><img class="captcha-image" :src="captchaImage" alt="验证码" @click="loadCaptcha"
            /></template>
          </el-input>
        </el-form-item>
        <div class="login-options">
          <el-checkbox v-model="form.remember">记住账号</el-checkbox><span>请使用内网授权账号登录</span>
        </div>
        <el-button type="primary" size="large" class="login-button" :loading="loading" @click="submit">登 录</el-button>
      </el-form>
      <div class="security-tip">
        <el-icon><Lock /></el-icon>系统操作全程记录，请妥善保管账号密码
      </div>
    </el-card>
    <footer>演示公安处 · 内部资料 注意保密</footer>
  </div>
</template>

<style scoped>
.login-error {
  margin-bottom: 16px;
}
</style>
