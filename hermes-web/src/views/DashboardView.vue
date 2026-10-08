<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'

interface Overview {
  windowHours: number
  from: string
  runTotal: number
  runByStatus: Record<string, number>
  stepTotal: number
  stepByStatus: Record<string, number>
  stepFailed: number
  runAvgDurationMs: number
  stepAvgDurationMs: number
  orderViolations: number
}

interface RunRow {
  runId: string
  agentCode?: string
  bizKey?: string
  status: string
  startTime?: string
  endTime?: string
  durationMs?: number
  errorMessage?: string
  stepTotal: number
  stepFailed: number
}

interface StepRow {
  id: number
  runId: string
  stepCode: string
  stepName?: string
  stepType?: string
  upstreamStep?: string
  status: string
  errorMessage?: string
  startTime?: string
  endTime?: string
  durationMs?: number
}

interface ViolationRow {
  runId: string
  stepCode: string
  stepStartTime?: string
  upstreamStep?: string
  upstreamEndTime?: string
}

const windowHours = ref(12)
const loading = ref(false)
const overview = ref<Overview | null>(null)
const runs = ref<RunRow[]>([])
const failures = ref<StepRow[]>([])
const violations = ref<ViolationRow[]>([])
const tab = ref('runs')

const stepsVisible = ref(false)
const steps = ref<StepRow[]>([])
const stepsTitle = ref('')

const reportVisible = ref(false)
const reportText = ref('')
const reportLoading = ref(false)

function statusType(s: string): 'success' | 'danger' | 'info' | 'warning' {
  if (s === 'SUCCESS' || s === 'SUCCEEDED') return 'success'
  if (s === 'FAILED') return 'danger'
  if (s === 'RUNNING') return 'warning'
  return 'info'
}

async function load() {
  loading.value = true
  try {
    const [o, r, f, v] = await Promise.all([
      http.get<Overview>('/dashboard/overview', { params: { windowHours: windowHours.value } }),
      http.get<RunRow[]>('/dashboard/runs', { params: { windowHours: windowHours.value, limit: 20 } }),
      http.get<StepRow[]>('/dashboard/failures', { params: { windowHours: windowHours.value, limit: 20 } }),
      http.get<ViolationRow[]>('/dashboard/order-violations', { params: { windowHours: windowHours.value } }),
    ])
    overview.value = o.data
    runs.value = r.data ?? []
    failures.value = f.data ?? []
    violations.value = v.data ?? []
  } catch (e) {
    ElMessage.error('加载看板失败：' + (e as Error).message)
  } finally {
    loading.value = false
  }
}

async function openSteps(row: RunRow) {
  stepsTitle.value = `步骤日志 · ${row.runId}`
  stepsVisible.value = true
  const { data } = await http.get<StepRow[]>(`/dashboard/runs/${row.runId}/steps`)
  steps.value = data ?? []
}

async function genReport() {
  reportLoading.value = true
  reportVisible.value = true
  reportText.value = ''
  try {
    const { data } = await http.post<{ windowHours: number; report: string }>('/dashboard/half-day-report', null, {
      params: { windowHours: windowHours.value },
    })
    reportText.value = data?.report ?? ''
  } catch (e) {
    ElMessage.error('生成报表失败：' + (e as Error).message)
  } finally {
    reportLoading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="view">
    <el-card shadow="never">
      <template #header>
        <div class="head">
          <b>看板监控</b>
          <span class="muted">只读聚合 ai_flow_run / ai_flow_step_log（白板：状态告警·时间告警·时间先后）</span>
          <el-select v-model="windowHours" size="small" style="width: 130px" @change="load">
            <el-option :value="1" label="近 1 小时" />
            <el-option :value="12" label="近 12 小时" />
            <el-option :value="24" label="近 24 小时" />
            <el-option :value="168" label="近 7 天" />
          </el-select>
          <el-button size="small" type="primary" @click="genReport">生成半天报表-AI</el-button>
        </div>
      </template>

      <el-row :gutter="12" class="stats">
        <el-col :span="4"><el-card shadow="never" class="stat"><div class="stat-v">{{ overview?.runTotal ?? 0 }}</div><div class="stat-l">运行总数</div></el-card></el-col>
        <el-col :span="4"><el-card shadow="never" class="stat"><div class="stat-v">{{ overview?.stepTotal ?? 0 }}</div><div class="stat-l">步骤总数</div></el-card></el-col>
        <el-col :span="4"><el-card shadow="never" class="stat"><div class="stat-v danger">{{ overview?.stepFailed ?? 0 }}</div><div class="stat-l">失败步骤</div></el-card></el-col>
        <el-col :span="4"><el-card shadow="never" class="stat"><div class="stat-v danger">{{ overview?.orderViolations ?? 0 }}</div><div class="stat-l">时序倒挂</div></el-card></el-col>
        <el-col :span="4"><el-card shadow="never" class="stat"><div class="stat-v">{{ overview?.runAvgDurationMs ?? 0 }}</div><div class="stat-l">平均运行耗时(ms)</div></el-card></el-col>
        <el-col :span="4"><el-card shadow="never" class="stat"><div class="stat-v">{{ overview?.stepAvgDurationMs ?? 0 }}</div><div class="stat-l">平均步骤耗时(ms)</div></el-card></el-col>
      </el-row>

      <div v-if="overview" class="status-bar">
        <span class="muted">运行状态：</span>
        <el-tag v-for="(n, k) in overview.runByStatus" :key="k" :type="statusType(String(k))" size="small" class="tag">{{ k }}={{ n }}</el-tag>
        <span class="muted">步骤状态：</span>
        <el-tag v-for="(n, k) in overview.stepByStatus" :key="k" :type="statusType(String(k))" size="small" class="tag">{{ k }}={{ n }}</el-tag>
      </div>
    </el-card>

    <el-card shadow="never">
      <el-tabs v-model="tab">
        <el-tab-pane label="运行记录" name="runs">
          <el-table :data="runs" border stripe size="small">
            <el-table-column label="运行" width="130">
              <template #default="{ row }">
                <el-button link type="primary" @click="openSteps(row)">{{ row.runId }}</el-button>
              </template>
            </el-table-column>
            <el-table-column prop="agentCode" label="智能体" width="140" />
            <el-table-column prop="bizKey" label="业务键" min-width="140" />
            <el-table-column label="状态" width="110">
              <template #default="{ row }"><el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="startTime" label="开始" width="170" />
            <el-table-column prop="durationMs" label="耗时(ms)" width="100" />
            <el-table-column label="步骤" width="110">
              <template #default="{ row }">{{ row.stepTotal }} / 失败 {{ row.stepFailed }}</template>
            </el-table-column>
            <el-table-column prop="errorMessage" label="错误" min-width="180" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="`失败步骤（${failures.length}）`" name="failures">
          <el-table :data="failures" border stripe size="small">
            <el-table-column prop="runId" label="运行" width="130" />
            <el-table-column prop="stepCode" label="步骤" width="150" />
            <el-table-column prop="stepName" label="名称" min-width="140" />
            <el-table-column prop="upstreamStep" label="上游" width="130" />
            <el-table-column prop="startTime" label="开始" width="170" />
            <el-table-column prop="durationMs" label="耗时(ms)" width="100" />
            <el-table-column prop="errorMessage" label="错误原因" min-width="220" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="`时序倒挂（${violations.length}）`" name="violations">
          <el-table :data="violations" border stripe size="small">
            <el-table-column prop="runId" label="运行" width="130" />
            <el-table-column prop="stepCode" label="步骤" width="150" />
            <el-table-column prop="stepStartTime" label="步骤开始" width="180" />
            <el-table-column prop="upstreamStep" label="上游" width="140" />
            <el-table-column prop="upstreamEndTime" label="上游结束" width="180" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-drawer v-model="stepsVisible" :title="stepsTitle" size="640px">
      <el-table :data="steps" border size="small">
        <el-table-column prop="stepCode" label="步骤" width="130" />
        <el-table-column prop="stepType" label="类型" width="80" />
        <el-table-column prop="upstreamStep" label="上游" width="110" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="startTime" label="开始" width="160" />
        <el-table-column prop="endTime" label="结束" width="160" />
        <el-table-column prop="durationMs" label="耗时(ms)" width="90" />
        <el-table-column prop="errorMessage" label="错误" min-width="140" />
      </el-table>
    </el-drawer>

    <el-dialog v-model="reportVisible" title="半天报表 · AI" width="760px">
      <div v-loading="reportLoading">
        <pre class="report">{{ reportText || '生成中…' }}</pre>
      </div>
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
.head .el-select,
.head .el-button {
  margin-left: auto;
}
.head .el-button {
  margin-left: 0;
}
.muted {
  color: #909399;
  font-size: 12px;
}
.stats {
  margin-bottom: 10px;
}
.stat {
  text-align: center;
}
.stat-v {
  font-size: 22px;
  font-weight: 700;
}
.stat-v.danger {
  color: #f56c6c;
}
.stat-l {
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
}
.status-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.tag {
  margin-right: 2px;
}
.report {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  max-height: 60vh;
  overflow: auto;
}
</style>
