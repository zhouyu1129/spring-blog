<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { User, Edit, Mail, Lock } from 'lucide-vue-next'
import { authApi } from '@/api'
import { useAuth } from '@/composables/useAuth'
import { useMessages } from '@/composables/useMessages'

const router = useRouter()
const { user } = useAuth()
const { addMessage } = useMessages()

const nickname = ref('')
const realName = ref('')
const mobile = ref('')
const gender = ref('')
const loading = ref(false)

onMounted(() => {
  if (user.value) {
    nickname.value = user.value.nickname || ''
    realName.value = user.value.real_name || ''
    mobile.value = user.value.mobile || ''
    gender.value = user.value.gender || ''
  }
})

async function handleSubmit() {
  loading.value = true
  try {
    const res = await authApi.editProfile({ nickname: nickname.value, real_name: realName.value, mobile: mobile.value, gender: gender.value })
    if (res.ok) {
      addMessage('个人资料更新成功', 'success')
      router.push({ name: 'profile' })
    } else { addMessage('更新失败', 'error') }
  } catch (e) { addMessage('更新失败', 'error') }
  finally { loading.value = false }
}
</script>

<template>
  <div class="grid md:grid-cols-4 gap-6">
    <div class="bg-white rounded-xl shadow-sm border border-zinc-100 h-fit">
      <div class="bg-emerald-700 text-white px-4 py-3 rounded-t-xl"><h5 class="font-semibold text-sm">用户中心</h5></div>
      <div class="divide-y">
        <router-link to="/user/profile" class="flex items-center gap-2 px-4 py-2.5 text-sm hover:bg-zinc-50 transition-colors"><User :size="14" /> 个人信息</router-link>
        <router-link to="/user/profile/edit" class="flex items-center gap-2 px-4 py-2.5 text-sm bg-amber-50 text-amber-700 font-medium"><Edit :size="14" /> 编辑资料</router-link>
        <router-link to="/user/profile/change-email" class="flex items-center gap-2 px-4 py-2.5 text-sm hover:bg-zinc-50 transition-colors"><Mail :size="14" /> 修改邮箱</router-link>
        <router-link to="/user/profile/change-password" class="flex items-center gap-2 px-4 py-2.5 text-sm hover:bg-zinc-50 transition-colors"><Lock :size="14" /> 修改密码</router-link>
      </div>
    </div>
    <div class="md:col-span-3">
      <div class="bg-white rounded-xl shadow-sm border border-zinc-100">
        <div class="bg-amber-500 text-white px-6 py-4 rounded-t-xl"><h5 class="font-semibold">编辑个人资料</h5></div>
        <div class="p-6">
          <form @submit.prevent="handleSubmit">
            <div class="mb-4"><label class="block text-sm font-medium mb-1.5">昵称</label><input v-model="nickname" type="text" maxlength="20" class="w-full border border-zinc-300 rounded-md px-4 py-2.5 text-sm focus:outline-none focus:border-amber-500" /><p class="text-xs text-zinc-400 mt-1">可选，最多20个字符</p></div>
            <div class="mb-4"><label class="block text-sm font-medium mb-1.5">真实姓名</label><input v-model="realName" type="text" maxlength="50" class="w-full border border-zinc-300 rounded-md px-4 py-2.5 text-sm focus:outline-none focus:border-amber-500" /></div>
            <div class="mb-4"><label class="block text-sm font-medium mb-1.5">手机号</label><input v-model="mobile" type="tel" maxlength="11" class="w-full border border-zinc-300 rounded-md px-4 py-2.5 text-sm focus:outline-none focus:border-amber-500" /></div>
            <div class="mb-4"><label class="block text-sm font-medium mb-1.5">性别</label><select v-model="gender" class="w-full border border-zinc-300 rounded-md px-4 py-2.5 text-sm focus:outline-none focus:border-amber-500"><option value="">请选择</option><option value="male">男</option><option value="female">女</option><option value="other">其他</option></select></div>
            <div class="flex justify-end gap-3">
              <router-link to="/user/profile" class="bg-zinc-200 text-zinc-700 px-4 py-2 rounded-md hover:bg-zinc-300 transition-colors text-sm">取消</router-link>
              <button type="submit" :disabled="loading" class="bg-amber-500 text-white px-4 py-2 rounded-md hover:bg-amber-400 transition-colors text-sm disabled:opacity-50">{{ loading ? '保存中...' : '保存修改' }}</button>
            </div>
          </form>
        </div>
      </div>
    </div>
  </div>
</template>
