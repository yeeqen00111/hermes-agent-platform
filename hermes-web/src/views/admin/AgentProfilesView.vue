<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'

interface AgentProfile {
  id?: number
  agentCode: string
  name: string
  description?: string
  systemPrompt?: string
  modelProvider?: string
  modelName?: string
  temperature?: number
  maxTokens?: number
  executionMode?: string
  flowDefinition?: string
  triggerType?: string
  status?: string
  currentVersion?: number
}

interface AgentVersion {
  version: number
  publishBy?: string
  publishTime?: string
}

interface DryRunResult {
  success: boolean
  message?: string
  agentCode?: string
  executionMode?: string
  fixedFlow?: boolean
  runId?: number
  reply?: string
  toolEvents?: unknown[]
}

const MODES = ['LLM_DRIVEN', 'FIXED_FLOW']

const agents = ref<AgentProfile[]>([])
const loading = ref(false)

const dialogVisible = ref(false)
const saving = ref(false)
const form = reactive<AgentProfile>({ agentCode: '', name: '', executionMode: 'LLM_DRIVEN', status: 'DRAFT' })

const verVisible = ref(false)
const versions = ref<AgentVersion[]>([])
const verAgent = ref<AgentProfile | null>(null)

const runVisible = ref(false)
const runAgent = ref<AgentProfile | null>(null)
const runForm = reactive<{ message: string; userId: number }>({ message: '', userId: 1 })
const runResult = ref<DryRunResult | null>(null)
const running = ref(false)

async function load() {
  loading.value = true
  try {
    const { data } = await http.get<AgentProfile[]>('/agent-profiles')
    agents.value = data ?? []
  } catch (e) {
    ElMessage.error('加载智能体失败：' + (e as Error).message)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, {
    id: undefined,
    agentCode: '',
    name: '',
    description: '',
    systemPrompt: '',
    modelProvider: 'deepseek',
    modelName: 'deepseek-chat',
    temperature: 0.7,
    maxTokens: 2048,
    executionMode: 'LLM_DRIVEN',
    flowDefinition: '',
    triggerType: 'MANUAL',
    status: 'DRAFT',
  })
  dialogVisible.value = true
}

function openEdit(row: AgentProfile) {
  Object.assign(form, row)
  dialogVisible.value = true
}

async function save() {
  if (!form.agentCode || !form.name) {
    ElMessage.warning('agentCode 与 name 必填')
    return
  }
  saving.value = true
  try {
    if (form.id) {
      await http.put(`/agent-profiles/${form.id}`, { ...form })
    } else {
      await http.post('/agent-profiles', { ...form })
    }
    ElMessage.success('已保存（草稿）')
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error('保存失败：' + (e as Error).message)
  } finally {
    saving.value = false
  }
}

async function publish(row: AgentProfile) {
  if (!row.id) return
  const { data } = await http.post<{ success: boolean; version?: number }>(`/agent-profiles/${row.id}/publish`)
  if (data?.success) ElMessage.success('已发布 v' + data.version)
  await load()
}

async function openVersions(row: AgentProfile) {
  verAgent.value = row
  verVisible.value = true
  if (!row.id) return
  const { data } = await http.get<{ success: boolean; data: AgentVersion[] }>(`/agent-profiles/${row.id}/versions`)
  versions.value = data?.success ? data.data ?? [] : []
}

async function rollback(v: number) {
  if (!verAgent.value?.id) return
  await http.post(`/agent-profiles/${verAgent.value.id}/rollback`, null, { params: { version: v } })
  ElMessage.success('已回滚到 v' + v)
  verVisible.value = false
  await load()
}

function openRun(row: AgentProfile) {
  runAgent.value = row
  runForm.message = ''
  runResult.value = null
  runVisible.value = true
}

async function doRun() {
  if (!runAgent.value?.id || !runForm.message.trim()) {
    ElMessage.warning('请输入试跑消息')
    return
  }
  running.value = true
  runResult.value = null
  try {
    const { data } = await http.post<DryRunResult>(`/agent-profiles/${runAgent.value.id}/dry-run`, { ...runForm })
    runResult.value = data
  } catch (e) {
    ElMessage.error('试跑失败：' + (e as Error).message)
  } finally {
    running.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="view">
    <el-card shadow="never">
      <template #header>
        <div class="head">
          <b>Agent · 智能体配置</b>
          <span class="muted">草稿 → 发布（不可变快照+版本指针）；发布前用「试跑」验</span>
          <el-button type="primary" size="small" @click="openCreate">新建智能体</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="agents" border stripe size="small">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="agentCode" label="编码" width="150" />
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column prop="executionMode" label="执行模式" width="120" />
        <el-table-column label="模型" width="180">
          <template #default="{ row }">{{ row.modelProvider }}/{{ row.modelName }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" />
        <el-table-column prop="currentVersion" label="版本" width="70" />
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="success" size="small" @click="publish(row)">发布</el-button>
            <el-button link type="info" size="small" @click="openVersions(row)">版本</el-button>
            <el-button link type="warning" size="small" @click="openRun(row)">试跑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑智能体' : '新建智能体'" width="700px">
      <el-form label-width="110px">
        <el-form-item label="编码" required><el-input v-model="form.agentCode" :disabled="!!form.id" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" /></el-form-item>
        <el-form-item label="执行模式">
          <el-select v-model="form.executionMode" style="width: 100%">
            <el-option v-for="m in MODES" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="模型供应商"><el-input v-model="form.modelProvider" /></el-form-item>
        <el-form-item label="模型名"><el-input v-model="form.modelName" /></el-form-item>
        <el-form-item label="温度"><el-input-number v-model="form.temperature" :min="0" :max="2" :step="0.1" /></el-form-item>
        <el-form-item label="最大 Token"><el-input-number v-model="form.maxTokens" :min="1" :step="256" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="DRAFT" value="DRAFT" />
            <el-option label="PUBLISHED" value="PUBLISHED" />
          </el-select>
        </el-form-item>
        <el-form-item label="SOUL 正文">
          <el-input v-model="form.systemPrompt" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="固定流程 JSON">
          <el-input v-model="form.flowDefinition" type="textarea" :rows="4" placeholder="FIXED_FLOW 时填写 steps JSON" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="verVisible" :title="`版本历史 · ${verAgent?.name ?? ''}`" width="480px">
      <el-table :data="versions" border size="small">
        <el-table-column prop="version" label="版本" width="80" />
        <el-table-column prop="publishBy" label="发布人" width="120" />
        <el-table-column prop="publishTime" label="发布时间" min-width="170" />
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button link type="danger" size="small" @click="rollback(row.version)">回滚</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="runVisible" :title="`试跑 · ${runAgent?.name ?? ''}`" width="680px">
      <el-alert
        type="info"
        :closable="false"
        title="试跑不建会话、不落聊天历史；无审批通道，WRITE/CONTROLLED 工具一律拒绝（契约 §3.5）"
      />
      <el-form label-width="80px" class="run-form">
        <el-form-item label="消息">
          <el-input v-model="runForm.message" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="用户ID"><el-input-number v-model="runForm.userId" :min="0" /></el-form-item>
      </el-form>
      <el-button type="primary" :loading="running" @click="doRun">运行</el-button>
      <el-card v-if="runResult" class="run-result" shadow="never">
        <div class="run-head">
          <el-tag size="small" :type="runResult.success ? 'success' : 'danger'">
            {{ runResult.success ? '成功' : '失败' }}
          </el-tag>
          <span class="muted">{{ runResult.agentCode }} · {{ runResult.executionMode }} · fixedFlow={{ runResult.fixedFlow }}</span>
        </div>
        <pre class="reply">{{ runResult.reply ?? runResult.message }}</pre>
        <div v-if="runResult.toolEvents?.length" class="muted">工具事件 {{ runResult.toolEvents.length }} 条</div>
      </el-card>
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
.run-form {
  margin-top: 12px;
}
.run-result {
  margin-top: 12px;
}
.run-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.reply {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  background: #fafafa;
  padding: 8px;
  border-radius: 4px;
  max-height: 260px;
  overflow: auto;
}
</style>
