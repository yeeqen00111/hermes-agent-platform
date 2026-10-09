<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'

interface Scope {
  lookbackHours?: number
  projectName?: string
  alertType?: string
  level?: string
}

interface AlertReport {
  id?: number
  code: string
  name: string
  frequencyHours: number
  scope?: string
  prompt?: string
  channelCode?: string
  recipient?: string
  lastSendTime?: string
  lastContent?: string
  enabled: number
  remark?: string
}

const loading = ref(false)
const reports = ref<AlertReport[]>([])

const dialog = ref(false)
const saving = ref(false)
const form = reactive<{
  id?: number
  code: string
  name: string
  frequencyHours: number
  prompt: string
  channelCode: string
  recipient: string
  enabled: number
  remark: string
  scope: Scope
}>({
  code: '',
  name: '',
  frequencyHours: 2,
  prompt: '请对比近两小时告警，指出重点系统与变化趋势，并给出处置建议。',
  channelCode: '',
  recipient: '',
  enabled: 1,
  remark: '',
  scope: { lookbackHours: 2, projectName: '', alertType: '', level: '' },
})

const runDialog = ref(false)
const runTarget = ref<AlertReport | null>(null)
const running = ref(false)
const runScope = reactive<Scope>({ lookbackHours: 2, projectName: '', alertType: '', level: '' })
const previewMd = ref('')

const contentDialog = ref(false)
const contentTarget = ref<AlertReport | null>(null)

function parseScope(raw?: string): Scope {
  if (!raw) return { lookbackHours: 2, projectName: '', alertType: '', level: '' }
  try {
    return JSON.parse(raw)
  } catch {
    return { lookbackHours: 2, projectName: '', alertType: '', level: '' }
  }
}

async function load() {
  loading.value = true
  try {
    const { data } = await http.get<AlertReport[]>('/ops/alert-report/configs')
    reports.value = data ?? []
  } catch (e) {
    ElMessage.error('加载告警报表配置失败：' + (e as Error).message)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, {
    id: undefined,
    code: '',
    name: '',
    frequencyHours: 2,
    prompt: '请对比近两小时告警，指出重点系统与变化趋势，并给出处置建议。',
    channelCode: '',
    recipient: '',
    enabled: 1,
    remark: '',
    scope: { lookbackHours: 2, projectName: '', alertType: '', level: '' },
  })
  dialog.value = true
}

function openEdit(row: AlertReport) {
  Object.assign(form, {
    ...row,
    prompt: row.prompt ?? '',
    channelCode: row.channelCode ?? '',
    recipient: row.recipient ?? '',
    remark: row.remark ?? '',
    scope: parseScope(row.scope),
  })
  dialog.value = true
}

async function save() {
  if (!form.code || !form.name) {
    ElMessage.warning('编码与名称必填')
    return
  }
  saving.value = true
  try {
    const scope = {
      lookbackHours: form.scope.lookbackHours ?? 2,
      projectName: form.scope.projectName || undefined,
      alertType: form.scope.alertType || undefined,
      level: form.scope.level || undefined,
    }
    await http.post('/ops/alert-report/configs', {
      id: form.id,
      code: form.code,
      name: form.name,
      frequencyHours: form.frequencyHours,
      scope: JSON.stringify(scope),
      prompt: form.prompt,
      channelCode: form.channelCode,
      recipient: form.recipient,
      enabled: form.enabled,
      remark: form.remark,
    })
    ElMessage.success('已保存')
    dialog.value = false
    await load()
  } catch (e) {
    ElMessage.error('保存失败：' + (e as Error).message)
  } finally {
    saving.value = false
  }
}

async function remove(row: AlertReport) {
  await ElMessageBox.confirm(`删除报表 ${row.code}？`, '提示', { type: 'warning' })
  await http.delete(`/ops/alert-report/configs/${row.id}`)
  await load()
}

function openRun(row: AlertReport) {
  runTarget.value = row
  Object.assign(runScope, parseScope(row.scope))
  previewMd.value = ''
  runDialog.value = true
}

function scopePayload() {
  return {
    lookbackHours: runScope.lookbackHours ?? 2,
    projectName: runScope.projectName || undefined,
    alertType: runScope.alertType || undefined,
    level: runScope.level || undefined,
  }
}

async function preview() {
  running.value = true
  try {
    const { data } = await http.post('/ops/alert-report/preview', {
      prompt: runTarget.value?.prompt,
      ...scopePayload(),
    })
    previewMd.value = String(data.markdown ?? '')
    ElMessage.success(`已生成预览（命中 ${data.recordCount} 条）`)
  } catch (e) {
    ElMessage.error('生成失败：' + (e as Error).message)
  } finally {
    running.value = false
  }
}

async function sendNow() {
  if (!runTarget.value) return
  running.value = true
  try {
    const { data } = await http.post(`/ops/alert-report/configs/${runTarget.value.code}/run`, scopePayload())
    previewMd.value = String(data.markdown ?? '')
    ElMessage[data.sent ? 'success' : 'warning'](data.sent ? '已发送' : `未发送（${data.status}）`)
    await load()
  } catch (e) {
    ElMessage.error('发送失败：' + (e as Error).message)
  } finally {
    running.value = false
  }
}

function viewContent(row: AlertReport) {
  contentTarget.value = row
  contentDialog.value = true
}

onMounted(load)
</script>

<template>
  <div class="view">
    <el-card shadow="never">
      <template #header>
        <div class="head">
          <b>业务层 · 智能运维 · 【AI】智能告警报表</b>
          <span class="muted">发送频率 / 发送内容范围 / 提示词 / 发送通道；定时发送 + 人工选范围</span>
          <el-button type="primary" size="small" @click="openCreate">新建报表</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="reports" border stripe size="small">
        <el-table-column prop="code" label="编码" width="130" />
        <el-table-column prop="name" label="名称" min-width="130" />
        <el-table-column label="发送频率" width="110">
          <template #default="{ row }">每 {{ row.frequencyHours }} 小时</template>
        </el-table-column>
        <el-table-column label="发送内容" min-width="220">
          <template #default="{ row }">{{ parseScope(row.scope).lookbackHours ?? 2 }} 小时内 · {{ parseScope(row.scope).projectName || '全部项目' }} · {{ parseScope(row.scope).alertType || '全部类型' }} · {{ parseScope(row.scope).level || '全部级别' }}</template>
        </el-table-column>
        <el-table-column prop="channelCode" label="发送通道" width="120" />
        <el-table-column prop="recipient" label="接收人" min-width="140" show-overflow-tooltip />
        <el-table-column prop="lastSendTime" label="上次发送" min-width="170" />
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button link type="success" size="small" @click="openRun(row)">人工触发</el-button>
            <el-button link type="primary" size="small" @click="viewContent(row)" :disabled="!row.lastContent">看正文</el-button>
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialog" :title="form.id ? '编辑告警报表' : '新建告警报表'" width="640px">
      <el-form label-width="110px">
        <el-form-item label="编码" required><el-input v-model="form.code" :disabled="!!form.id" placeholder="如 alert-2h" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="发送频率"><el-input-number v-model="form.frequencyHours" :min="1" :max="168" /> <span class="muted">小时</span></el-form-item>
        <el-divider content-position="left">发送内容（范围）</el-divider>
        <el-form-item label="回溯"><el-input-number v-model="form.scope.lookbackHours" :min="1" :max="168" /> <span class="muted">小时</span></el-form-item>
        <el-form-item label="项目"><el-input v-model="form.scope.projectName" placeholder="空=全部项目" /></el-form-item>
        <el-form-item label="告警类型">
          <el-select v-model="form.scope.alertType" clearable placeholder="全部类型" style="width: 200px">
            <el-option label="ALERT / METRIC" value="ALERT" />
            <el-option label="PROMOTE" value="PROMOTE" />
            <el-option label="METRIC" value="METRIC" />
          </el-select>
        </el-form-item>
        <el-form-item label="日志级别"><el-input v-model="form.scope.level" placeholder="如 ERROR（空=全部）" /></el-form-item>
        <el-divider content-position="left">提示词</el-divider>
        <el-form-item label="提示词"><el-input v-model="form.prompt" type="textarea" :rows="3" /></el-form-item>
        <el-divider content-position="left">发送通道</el-divider>
        <el-form-item label="通道"><el-input v-model="form.channelCode" placeholder="通知通道编码" /></el-form-item>
        <el-form-item label="接收人"><el-input v-model="form.recipient" placeholder="邮箱 / 飞书账号" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="runDialog" :title="`人工触发 · ${runTarget?.name ?? ''}`" width="720px">
      <el-form label-width="90px" class="inline-form">
        <el-form-item label="回溯"><el-input-number v-model="runScope.lookbackHours" :min="1" :max="168" /> <span class="muted">小时</span></el-form-item>
        <el-form-item label="项目"><el-input v-model="runScope.projectName" placeholder="全部" style="width: 160px" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="runScope.alertType" clearable placeholder="全部" style="width: 140px">
            <el-option label="ALERT" value="ALERT" />
            <el-option label="PROMOTE" value="PROMOTE" />
            <el-option label="METRIC" value="METRIC" />
          </el-select>
        </el-form-item>
        <el-form-item label="级别"><el-input v-model="runScope.level" placeholder="全部" style="width: 120px" /></el-form-item>
      </el-form>
      <pre v-if="previewMd" class="md">{{ previewMd }}</pre>
      <template #footer>
        <el-button @click="runDialog = false">关闭</el-button>
        <el-button :loading="running" @click="preview">生成预览</el-button>
        <el-button type="primary" :loading="running" @click="sendNow">立即发送</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="contentDialog" :title="`报表正文 · ${contentTarget?.name ?? ''}`" width="720px">
      <pre class="md">{{ contentTarget?.lastContent }}</pre>
    </el-dialog>
  </div>
</template>

<style scoped>
.view { display: flex; flex-direction: column; gap: 12px; }
.head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.head .el-button { margin-left: auto; }
.muted { color: #909399; font-size: 12px; }
.inline-form { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.inline-form :deep(.el-form-item) { margin-bottom: 0; }
.md { background: #f5f7fa; padding: 12px; border-radius: 4px; font-size: 12px; max-height: 420px; overflow: auto; white-space: pre-wrap; }
</style>
