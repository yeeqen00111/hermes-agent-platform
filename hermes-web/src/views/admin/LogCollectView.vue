<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'

interface LogChannel {
  id?: number
  code: string
  name: string
  channelType: string
  config?: string
  enabled: number
  remark?: string
}

interface LogParseRule {
  id?: number
  code: string
  name: string
  channelCode?: string
  systemField?: string
  timeField?: string
  timeFormat?: string
  levelField?: string
  levelMapping?: string
  contentField?: string
  serviceField?: string
  sampleJson?: string
  enabled: number
  remark?: string
}

const CHANNEL_TYPES = [
  { value: 'KAFKA', label: 'kafka（目前唯一支持）' },
  { value: 'MQ', label: '消息队列' },
  { value: 'FILEBEAT_JSON', label: '兼容 filebeat.log' },
]

const tab = ref('channel')
const loading = ref(false)
const channels = ref<LogChannel[]>([])
const rules = ref<LogParseRule[]>([])

const channelDialog = ref(false)
const channelSaving = ref(false)
const channelForm = reactive<LogChannel>({ code: '', name: '', channelType: 'KAFKA', config: '', enabled: 1, remark: '' })

const ruleDialog = ref(false)
const ruleSaving = ref(false)
const emptyRule = (): LogParseRule => ({
  code: '',
  name: '',
  channelCode: '',
  systemField: 'app',
  timeField: '@timestamp',
  timeFormat: 'yyyy-MM-dd HH:mm:ss.SSS',
  levelField: 'level',
  levelMapping: '{"WARN":"WARNING"}',
  contentField: 'message',
  serviceField: 'service',
  sampleJson: '',
  enabled: 1,
  remark: '',
})
const ruleForm = reactive<LogParseRule>(emptyRule())

const exampleDialog = ref(false)
const exampleContent = ref('')

const previewDialog = ref(false)
const previewContent = ref('')

async function loadChannels() {
  const { data } = await http.get<LogChannel[]>('/admin/log/channels')
  channels.value = data ?? []
}

async function loadRules() {
  const { data } = await http.get<LogParseRule[]>('/admin/log/parse-rules')
  rules.value = data ?? []
}

async function load() {
  loading.value = true
  try {
    await Promise.all([loadChannels(), loadRules()])
  } catch (e) {
    ElMessage.error('加载日志采集配置失败：' + (e as Error).message)
  } finally {
    loading.value = false
  }
}

// ---------- 采集通道 ----------

function openCreateChannel() {
  Object.assign(channelForm, { id: undefined, code: '', name: '', channelType: 'KAFKA', config: '', enabled: 1, remark: '' })
  channelDialog.value = true
}

function openEditChannel(row: LogChannel) {
  Object.assign(channelForm, row)
  channelDialog.value = true
}

async function saveChannel() {
  if (!channelForm.code || !channelForm.name) {
    ElMessage.warning('编码与名称必填')
    return
  }
  channelSaving.value = true
  try {
    await http.post('/admin/log/channels', { ...channelForm })
    ElMessage.success('已保存')
    channelDialog.value = false
    await loadChannels()
  } catch (e) {
    ElMessage.error('保存失败：' + (e as Error).message)
  } finally {
    channelSaving.value = false
  }
}

async function removeChannel(row: LogChannel) {
  await ElMessageBox.confirm(`删除采集通道 ${row.code}？`, '提示', { type: 'warning' })
  await http.delete(`/admin/log/channels/${row.id}`)
  await loadChannels()
}

async function showExample(row: LogChannel) {
  const { data } = await http.get<{ success: boolean; content?: string; message?: string }>(
    `/admin/log/channels/${row.code}/filebeat-example`,
  )
  if (!data.success) {
    ElMessage.error(data.message ?? '生成失败')
    return
  }
  exampleContent.value = data.content ?? ''
  exampleDialog.value = true
}

// ---------- 解析规则 ----------

function openCreateRule() {
  Object.assign(ruleForm, emptyRule())
  ruleDialog.value = true
}

function openEditRule(row: LogParseRule) {
  Object.assign(ruleForm, emptyRule(), row)
  ruleDialog.value = true
}

async function saveRule() {
  if (!ruleForm.code || !ruleForm.name) {
    ElMessage.warning('编码与名称必填')
    return
  }
  ruleSaving.value = true
  try {
    await http.post('/admin/log/parse-rules', { ...ruleForm })
    ElMessage.success('已保存')
    ruleDialog.value = false
    await loadRules()
  } catch (e) {
    ElMessage.error('保存失败：' + (e as Error).message)
  } finally {
    ruleSaving.value = false
  }
}

async function removeRule(row: LogParseRule) {
  await ElMessageBox.confirm(`删除解析规则 ${row.code}？`, '提示', { type: 'warning' })
  await http.delete(`/admin/log/parse-rules/${row.id}`)
  await loadRules()
}

async function preview() {
  const { data } = await http.post<Record<string, unknown>>('/admin/log/parse-rules/preview', { ...ruleForm })
  previewContent.value = JSON.stringify(data, null, 2)
  previewDialog.value = true
}

onMounted(load)
</script>

<template>
  <div class="view">
    <el-card shadow="never">
      <template #header>
        <div class="head">
          <b>系统管理 · 日志采集</b>
          <span class="muted">图2 日志中枢：filebeat → kafka → 日志采集 → 日志解析（分级固化在「智能运维·日志告警规则」）</span>
        </div>
      </template>

      <el-tabs v-model="tab">
        <el-tab-pane label="采集通道" name="channel">
          <div class="toolbar">
            <span class="muted">配置从哪采集：目前只支持 kafka，兼容 filebeat.log / 消息队列</span>
            <el-button type="primary" size="small" @click="openCreateChannel">新建通道</el-button>
          </div>
          <el-table v-loading="loading" :data="channels" border stripe size="small">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="code" label="编码" width="150" />
            <el-table-column prop="name" label="名称" min-width="140" />
            <el-table-column prop="channelType" label="类型" width="140" />
            <el-table-column prop="config" label="配置(JSON)" min-width="220" show-overflow-tooltip />
            <el-table-column label="启用" width="80">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="120" />
            <el-table-column label="操作" width="190" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openEditChannel(row)">编辑</el-button>
                <el-button link type="success" size="small" @click="showExample(row)">filebeat 示例</el-button>
                <el-button link type="danger" size="small" @click="removeChannel(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="JSON 解析规则" name="rule">
          <div class="toolbar">
            <span class="muted">五级解析：系统字段映射 / 时间字段+格式 / 日志级别映射 / 内容字段 / 服务字段映射</span>
            <el-button type="primary" size="small" @click="openCreateRule">新建规则</el-button>
          </div>
          <el-table v-loading="loading" :data="rules" border stripe size="small">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="code" label="编码" width="140" />
            <el-table-column prop="name" label="名称" min-width="130" />
            <el-table-column prop="channelCode" label="通道" width="110" />
            <el-table-column prop="systemField" label="系统字段" width="100" />
            <el-table-column prop="timeField" label="时间字段" width="110" />
            <el-table-column prop="timeFormat" label="时间格式" width="160" show-overflow-tooltip />
            <el-table-column prop="levelField" label="级别字段" width="100" />
            <el-table-column prop="contentField" label="内容字段" width="100" />
            <el-table-column prop="serviceField" label="服务字段" width="100" />
            <el-table-column label="启用" width="80">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openEditRule(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="removeRule(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="channelDialog" :title="channelForm.id ? '编辑采集通道' : '新建采集通道'" width="580px">
      <el-form label-width="110px">
        <el-form-item label="编码" required><el-input v-model="channelForm.code" :disabled="!!channelForm.id" placeholder="如 kafka-app" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="channelForm.name" /></el-form-item>
        <el-form-item label="通道类型">
          <el-select v-model="channelForm.channelType" style="width: 100%">
            <el-option v-for="t in CHANNEL_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="配置(JSON)">
          <el-input
            v-model="channelForm.config"
            type="textarea"
            :rows="4"
            placeholder='KAFKA：{"brokers":"kafka:9092","topic":"app-log","groupId":"hermes","path":"/var/log/app/*.log"}'
          />
        </el-form-item>
        <el-form-item label="启用"><el-switch v-model="channelForm.enabled" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="channelForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="channelDialog = false">取消</el-button>
        <el-button type="primary" :loading="channelSaving" @click="saveChannel">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="ruleDialog" :title="ruleForm.id ? '编辑解析规则' : '新建解析规则'" width="640px">
      <el-form label-width="120px">
        <el-form-item label="编码" required><el-input v-model="ruleForm.code" :disabled="!!ruleForm.id" placeholder="如 app-json" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="ruleForm.name" /></el-form-item>
        <el-form-item label="采集通道">
          <el-select v-model="ruleForm.channelCode" clearable style="width: 100%" placeholder="选择关联通道">
            <el-option v-for="c in channels" :key="c.code" :label="c.code" :value="c.code" />
          </el-select>
        </el-form-item>
        <el-divider content-position="left">五级解析</el-divider>
        <el-form-item label="系统字段映射"><el-input v-model="ruleForm.systemField" placeholder="如 app" /></el-form-item>
        <el-form-item label="时间字段"><el-input v-model="ruleForm.timeField" placeholder="如 @timestamp" /></el-form-item>
        <el-form-item label="时间格式"><el-input v-model="ruleForm.timeFormat" placeholder="如 yyyy-MM-dd HH:mm:ss.SSS" /></el-form-item>
        <el-form-item label="级别字段"><el-input v-model="ruleForm.levelField" placeholder="如 level" /></el-form-item>
        <el-form-item label="级别映射(JSON)"><el-input v-model="ruleForm.levelMapping" placeholder='{"WARN":"WARNING"}' /></el-form-item>
        <el-form-item label="内容字段"><el-input v-model="ruleForm.contentField" placeholder="如 message" /></el-form-item>
        <el-form-item label="服务字段映射"><el-input v-model="ruleForm.serviceField" placeholder="如 service" /></el-form-item>
        <el-form-item label="示例日志(JSON)"><el-input v-model="ruleForm.sampleJson" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="ruleForm.enabled" :active-value="1" :inactive-value="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="preview">解析预览</el-button>
        <el-button @click="ruleDialog = false">取消</el-button>
        <el-button type="primary" :loading="ruleSaving" @click="saveRule">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="exampleDialog" title="filebeat 配置示例" width="640px">
      <el-input v-model="exampleContent" type="textarea" :rows="14" readonly />
      <template #footer>
        <el-button type="primary" @click="exampleDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="previewDialog" title="解析预览" width="560px">
      <el-input v-model="previewContent" type="textarea" :rows="12" readonly />
      <template #footer>
        <el-button type="primary" @click="previewDialog = false">关闭</el-button>
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
  flex-wrap: wrap;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.toolbar .el-button {
  margin-left: auto;
}
.muted {
  color: #909399;
  font-size: 12px;
}
</style>
