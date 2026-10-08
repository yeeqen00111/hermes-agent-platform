<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'

interface ToolCall {
  id: number
  traceId?: string
  sessionId?: string
  agentCode?: string
  toolCode?: string
  safetyLevel?: string
  arguments?: string
  success?: number
  errorCode?: string
  durationMs?: number
  approvalChoice?: string
  approvalBy?: number
  approvalTime?: string
  channel?: string
  createTime?: string
}

const filters = reactive<{ traceId: string; sessionId: string; toolCode: string; channel: string; limit: number }>({
  traceId: '',
  sessionId: '',
  toolCode: '',
  channel: '',
  limit: 100,
})

const calls = ref<ToolCall[]>([])
const loading = ref(false)

const detailVisible = ref(false)
const current = ref<ToolCall | null>(null)

async function load() {
  loading.value = true
  try {
    const params: Record<string, string | number> = { limit: filters.limit }
    if (filters.traceId) params.traceId = filters.traceId
    if (filters.sessionId) params.sessionId = filters.sessionId
    if (filters.toolCode) params.toolCode = filters.toolCode
    if (filters.channel) params.channel = filters.channel
    const { data } = await http.get<ToolCall[]>('/audit/tool-calls', { params })
    calls.value = data ?? []
  } catch (e) {
    ElMessage.error('加载审计失败：' + (e as Error).message)
  } finally {
    loading.value = false
  }
}

function reset() {
  filters.traceId = ''
  filters.sessionId = ''
  filters.toolCode = ''
  filters.channel = ''
  filters.limit = 100
  load()
}

function openDetail(row: ToolCall) {
  current.value = row
  detailVisible.value = true
}

onMounted(load)
</script>

<template>
  <div class="view">
    <el-card shadow="never">
      <template #header>
        <div class="head">
          <b>工具调用审计</b>
          <span class="muted">只读（契约 §17.1 所有动作可审计）；记录每次工具调用的入参（脱敏）、审批决策与渠道</span>
        </div>
      </template>

      <el-form :inline="true" class="filters">
        <el-form-item label="traceId"><el-input v-model="filters.traceId" size="small" style="width: 200px" clearable /></el-form-item>
        <el-form-item label="sessionId"><el-input v-model="filters.sessionId" size="small" style="width: 180px" clearable /></el-form-item>
        <el-form-item label="工具"><el-input v-model="filters.toolCode" size="small" style="width: 150px" clearable /></el-form-item>
        <el-form-item label="渠道"><el-input v-model="filters.channel" size="small" style="width: 120px" clearable /></el-form-item>
        <el-form-item label="条数"><el-input-number v-model="filters.limit" :min="1" :max="500" size="small" /></el-form-item>
        <el-form-item>
          <el-button type="primary" size="small" @click="load">查询</el-button>
          <el-button size="small" @click="reset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="calls" border stripe size="small">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="createTime" label="时间" width="170" />
        <el-table-column prop="toolCode" label="工具" width="150" />
        <el-table-column prop="safetyLevel" label="等级" width="100" />
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <el-tag :type="row.success ? 'success' : 'danger'" size="small">{{ row.success ? '成功' : '失败' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="errorCode" label="错误码" width="120" />
        <el-table-column prop="durationMs" label="耗时(ms)" width="100" />
        <el-table-column prop="channel" label="渠道" width="100" />
        <el-table-column prop="agentCode" label="智能体" width="130" />
        <el-table-column prop="approvalChoice" label="审批" width="130" />
        <el-table-column prop="sessionId" label="会话" width="140" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="detailVisible" title="调用详情" size="560px">
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="ID">{{ current?.id }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ current?.createTime }}</el-descriptions-item>
        <el-descriptions-item label="traceId">{{ current?.traceId }}</el-descriptions-item>
        <el-descriptions-item label="sessionId">{{ current?.sessionId }}</el-descriptions-item>
        <el-descriptions-item label="智能体">{{ current?.agentCode }}</el-descriptions-item>
        <el-descriptions-item label="工具">{{ current?.toolCode }}（{{ current?.safetyLevel }}）</el-descriptions-item>
        <el-descriptions-item label="渠道">{{ current?.channel }}</el-descriptions-item>
        <el-descriptions-item label="审批">{{ current?.approvalChoice }} / by {{ current?.approvalBy }} @ {{ current?.approvalTime }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ current?.durationMs }} ms</el-descriptions-item>
        <el-descriptions-item label="错误码">{{ current?.errorCode || '-' }}</el-descriptions-item>
      </el-descriptions>
      <h4>入参（脱敏）</h4>
      <pre class="args">{{ current?.arguments || '-' }}</pre>
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
  flex-wrap: wrap;
}
.muted {
  color: #909399;
  font-size: 12px;
}
.filters {
  flex-wrap: wrap;
}
.args {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  background: #fafafa;
  padding: 8px;
  border-radius: 4px;
  max-height: 320px;
  overflow: auto;
}
h4 {
  margin: 14px 0 6px;
}
</style>
