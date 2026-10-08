<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'

interface Repo {
  id?: number
  repoCode: string
  name: string
  repoUrl?: string
  defaultBranch?: string
  credentialId?: number
  workspaceDir?: string
  lastSyncTime?: string
  lastRevision?: string
  reviewPromptExtra?: string
  enabled: number
}

interface Credential {
  id: number
  credCode: string
  name: string
}

const repos = ref<Repo[]>([])
const creds = ref<Credential[]>([])
const loading = ref(false)

const dialogVisible = ref(false)
const saving = ref(false)
const form = reactive<Repo>({ repoCode: '', name: '', defaultBranch: 'main', enabled: 1 })

async function load() {
  loading.value = true
  try {
    const [r, c] = await Promise.all([http.get<Repo[]>('/review/repos'), http.get<Credential[]>('/review/credentials')])
    repos.value = r.data ?? []
    creds.value = c.data ?? []
  } catch (e) {
    ElMessage.error('加载仓库失败：' + (e as Error).message)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { id: undefined, repoCode: '', name: '', repoUrl: '', defaultBranch: 'main', credentialId: undefined, workspaceDir: '', reviewPromptExtra: '', enabled: 1 })
  dialogVisible.value = true
}

function openEdit(row: Repo) {
  Object.assign(form, row)
  dialogVisible.value = true
}

async function save() {
  if (!form.repoCode || !form.name) {
    ElMessage.warning('repoCode 与 name 必填')
    return
  }
  saving.value = true
  try {
    await http.post('/review/repos', { ...form })
    ElMessage.success('已保存')
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error('保存失败：' + (e as Error).message)
  } finally {
    saving.value = false
  }
}

async function sync(row: Repo) {
  if (!row.id) return
  const { data } = await http.post<{ success: boolean; head?: string; message?: string }>(`/review/repos/${row.id}/sync`)
  if (data?.success) ElMessage.success('已同步 HEAD=' + (data.head ?? ''))
  else ElMessage.error(data?.message ?? '同步失败')
  await load()
}

onMounted(load)
</script>

<template>
  <div class="view">
    <el-card shadow="never">
      <template #header>
        <div class="head">
          <b>系统管理 · 代码仓库</b>
          <span class="muted">评审对象仓库；凭据只存引用；同步拉取 HEAD 版本</span>
          <el-button type="primary" size="small" @click="openCreate">新建仓库</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="repos" border stripe size="small">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="repoCode" label="编码" width="140" />
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column prop="repoUrl" label="地址" min-width="220" />
        <el-table-column prop="defaultBranch" label="默认分支" width="110" />
        <el-table-column prop="lastRevision" label="最近版本" width="140" />
        <el-table-column prop="lastSyncTime" label="同步时间" width="170" />
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="success" size="small" @click="sync(row)">同步</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑仓库' : '新建仓库'" width="580px">
      <el-form label-width="100px">
        <el-form-item label="编码" required><el-input v-model="form.repoCode" :disabled="!!form.id" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="仓库地址"><el-input v-model="form.repoUrl" placeholder="https://gitlab.../x.git" /></el-form-item>
        <el-form-item label="默认分支"><el-input v-model="form.defaultBranch" /></el-form-item>
        <el-form-item label="凭据">
          <el-select v-model="form.credentialId" clearable style="width: 100%" placeholder="选择凭据">
            <el-option v-for="c in creds" :key="c.id" :label="`${c.name}（${c.credCode}）`" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="工作目录"><el-input v-model="form.workspaceDir" /></el-form-item>
        <el-form-item label="附加提示词"><el-input v-model="form.reviewPromptExtra" type="textarea" :rows="3" /></el-form-item>
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
