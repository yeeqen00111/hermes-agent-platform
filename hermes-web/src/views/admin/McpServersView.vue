<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'

interface McpServer {
  id?: number
  serverCode: string
  name: string
  baseUrl?: string
  apiKeyRef?: string
  enabled: number
  config?: string
}

interface McpTool {
  id: number
  toolCode: string
  displayName?: string
  safetyLevel?: string
  enabled?: number
}

const servers = ref<McpServer[]>([])
const loading = ref(false)

const dialogVisible = ref(false)
const saving = ref(false)
const form = reactive<McpServer>({ serverCode: '', name: '', enabled: 1, config: '' })

const toolsVisible = ref(false)
const current = ref<McpServer | null>(null)
const tools = ref<McpTool[]>([])

async function load() {
  loading.value = true
  try {
    const { data } = await http.get<McpServer[]>('/mcp/servers')
    servers.value = data ?? []
  } catch (e) {
    ElMessage.error('加载 MCP 服务失败：' + (e as Error).message)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { id: undefined, serverCode: '', name: '', baseUrl: '', apiKeyRef: '', enabled: 1, config: '' })
  dialogVisible.value = true
}

function openEdit(row: McpServer) {
  Object.assign(form, row)
  dialogVisible.value = true
}

async function save() {
  if (!form.serverCode || !form.name) {
    ElMessage.warning('serverCode 与 name 必填')
    return
  }
  saving.value = true
  try {
    await http.post('/mcp/servers', { ...form })
    ElMessage.success('已保存')
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error('保存失败：' + (e as Error).message)
  } finally {
    saving.value = false
  }
}

async function sync(row: McpServer) {
  const { data } = await http.post<{ success?: boolean; message?: string }>(`/mcp/servers/${row.serverCode}/sync`)
  ElMessage.success(data?.message ?? '已触发同步')
  await load()
}

async function openTools(row: McpServer) {
  current.value = row
  toolsVisible.value = true
  const { data } = await http.get<McpTool[]>(`/mcp/servers/${row.serverCode}/tools`)
  tools.value = data ?? []
}

async function remove(row: McpServer) {
  await ElMessageBox.confirm(`删除 MCP 服务 ${row.serverCode}？`, '提示', { type: 'warning' })
  await http.delete(`/mcp/servers/${row.serverCode}`)
  await load()
}

onMounted(load)
</script>

<template>
  <div class="view">
    <el-card shadow="never">
      <template #header>
        <div class="head">
          <b>Agent · MCP</b>
          <span class="muted">MCP 服务注册 + 工具同步（工具进入受控 Tool Registry）</span>
          <el-button type="primary" size="small" @click="openCreate">新建服务</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="servers" border stripe size="small">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="serverCode" label="编码" width="150" />
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column prop="baseUrl" label="地址" min-width="220" />
        <el-table-column prop="apiKeyRef" label="凭据引用" width="150" />
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="success" size="small" @click="sync(row)">同步</el-button>
            <el-button link type="info" size="small" @click="openTools(row)">工具</el-button>
            <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑 MCP 服务' : '新建 MCP 服务'" width="560px">
      <el-form label-width="100px">
        <el-form-item label="编码" required><el-input v-model="form.serverCode" :disabled="!!form.id" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="地址"><el-input v-model="form.baseUrl" placeholder="https://..." /></el-form-item>
        <el-form-item label="凭据引用"><el-input v-model="form.apiKeyRef" placeholder="环境变量名（不存明文）" /></el-form-item>
        <el-form-item label="配置 JSON"><el-input v-model="form.config" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="toolsVisible" :title="`工具 · ${current?.name ?? ''}`" size="520px">
      <el-table :data="tools" border size="small">
        <el-table-column prop="toolCode" label="工具编码" min-width="180" />
        <el-table-column prop="displayName" label="显示名" min-width="140" />
        <el-table-column prop="safetyLevel" label="安全等级" width="110" />
      </el-table>
    </el-drawer>
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
