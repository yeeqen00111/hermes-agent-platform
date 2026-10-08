<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'

interface Credential {
  id?: number
  credCode: string
  name: string
  credType?: string
  username?: string
  secretRef?: string
  enabled: number
}

const TYPES = ['GITLAB', 'GITHUB', 'GITEE', 'GENERIC']

const creds = ref<Credential[]>([])
const loading = ref(false)

const dialogVisible = ref(false)
const saving = ref(false)
const form = reactive<Credential>({ credCode: '', name: '', credType: 'GITLAB', enabled: 1 })

async function load() {
  loading.value = true
  try {
    const { data } = await http.get<Credential[]>('/review/credentials')
    creds.value = data ?? []
  } catch (e) {
    ElMessage.error('加载凭据失败：' + (e as Error).message)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { id: undefined, credCode: '', name: '', credType: 'GITLAB', username: '', secretRef: '', enabled: 1 })
  dialogVisible.value = true
}

function openEdit(row: Credential) {
  Object.assign(form, row)
  dialogVisible.value = true
}

async function save() {
  if (!form.credCode || !form.name) {
    ElMessage.warning('credCode 与 name 必填')
    return
  }
  saving.value = true
  try {
    await http.post('/review/credentials', { ...form })
    ElMessage.success('已保存')
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error('保存失败：' + (e as Error).message)
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="view">
    <el-card shadow="never">
      <template #header>
        <div class="head">
          <b>系统管理 · 凭据管理</b>
          <span class="muted">只存环境变量引用（secretRef），库中无明文</span>
          <el-button type="primary" size="small" @click="openCreate">新建凭据</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="creds" border stripe size="small">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="credCode" label="编码" width="160" />
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column prop="credType" label="类型" width="120" />
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column prop="secretRef" label="密钥引用" min-width="180" />
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑凭据' : '新建凭据'" width="520px">
      <el-form label-width="100px">
        <el-form-item label="编码" required><el-input v-model="form.credCode" :disabled="!!form.id" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.credType" style="width: 100%">
            <el-option v-for="t in TYPES" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="用户名"><el-input v-model="form.username" /></el-form-item>
        <el-form-item label="密钥引用">
          <el-input v-model="form.secretRef" placeholder="环境变量名，如 GITLAB_TOKEN_DEMO" />
        </el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.view {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.head .el-button {
  margin-left: auto;
}
.muted {
  color: #909399;
  font-size: 12px;
}
</style>
