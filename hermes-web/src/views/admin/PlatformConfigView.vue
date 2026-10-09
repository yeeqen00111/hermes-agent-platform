<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'

interface LogRow {
  time: string
  level: string
  logger: string
  thread: string
  message: string
}

interface CountRow {
  count: number
  [key: string]: unknown
}

const tab = ref('logs')

// 平台日志
const logs = ref<LogRow[]>([])
const logLoading = ref(false)
const logFilter = reactive({ level: '', keyword: '', limit: 200 })
const LEVEL_TAG: Record<string, string> = { ERROR: 'danger', WARN: 'warning', INFO: 'success', DEBUG: 'info', TRACE: 'info' }

// 用量分析
const usageDays = ref(7)
const usage = ref<Record<string, unknown> | null>(null)
const usageLoading = ref(false)

// 数据看板
const dashboard = ref<Record<string, unknown> | null>(null)
const dashLoading = ref(false)

const COUNT_LABEL: Record<string, string> = {
  agents: '智能体',
  sessions: '会话',
  messages: '消息',
  toolCalls: '工具调用',
  alerts: '告警记录',
  alertReports: '告警报表',
  bizMetrics: '业务指标',
  kbDocs: '知识库文档',
  opsGrants: '运维授权',
  repos: '代码仓库',
  projects: '项目',
  users: '人员',
}

async function loadLogs() {
  logLoading.value = true
  try {
    const { data } = await http.get<LogRow[]>('/admin/platform/logs', {
      params: { level: logFilter.level || undefined, keyword: logFilter.keyword || undefined, limit: logFilter.limit },
    })
    logs.value = data ?? []
  } catch (e) {
    ElMessage.error('加载平台日志失败：' + (e as Error).message)
  } finally {
    logLoading.value = false
  }
}

async function loadUsage() {
  usageLoading.value = true
  try {
    const { data } = await http.get('/admin/platform/usage', { params: { days: usageDays.value } })
    usage.value = data
  } catch (e) {
    ElMessage.error('加载用量分析失败：' + (e as Error).message)
  } finally {
    usageLoading.value = false
  }
}

async function loadDashboard() {
  dashLoading.value = true
  try {
    const { data } = await http.get('/admin/platform/dashboard')
    dashboard.value = data
  } catch (e) {
    ElMessage.error('加载数据看板失败：' + (e as Error).message)
  } finally {
    dashLoading.value = false
  }
}

onMounted(() => {
  loadLogs()
  loadUsage()
  loadDashboard()
})
</script>

<template>
  <div class="view">
    <el-card shadow="never">
      <template #header>
        <div class="head">
          <b>平台层 · 平台配置</b>
          <span class="muted">平台日志 / 大模型配置 / 用量分析 / 数据看板</span>
          <el-button size="small" @click="$router.push('/admin/agent/models')">大模型配置 →</el-button>
        </div>
      </template>

      <el-tabs v-model="tab">
        <el-tab-pane label="平台日志" name="logs">
          <div class="toolbar">
            <el-select v-model="logFilter.level" placeholder="全部级别" clearable style="width: 140px">
              <el-option v-for="l in ['ERROR', 'WARN', 'INFO', 'DEBUG']" :key="l" :label="l" :value="l" />
            </el-select>
            <el-input v-model="logFilter.keyword" placeholder="关键字" clearable style="width: 220px" @keyup.enter="loadLogs" />
            <el-button type="primary" size="small" @click="loadLogs">查询</el-button>
            <span class="muted">运行日志环形缓冲（最近 500 条）</span>
          </div>
          <el-table v-loading="logLoading" :data="logs" border stripe size="small" max-height="520">
            <el-table-column prop="time" label="时间" width="180" />
            <el-table-column label="级别" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="LEVEL_TAG[row.level] ?? 'info'">{{ row.level }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="thread" label="线程" width="130" />
            <el-table-column prop="logger" label="Logger" min-width="200" show-overflow-tooltip />
            <el-table-column prop="message" label="消息" min-width="320" show-overflow-tooltip />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="用量分析" name="usage">
          <div class="toolbar">
            <span>统计窗口</span>
            <el-input-number v-model="usageDays" :min="1" :max="90" size="small" />
            <span class="muted">天</span>
            <el-button type="primary" size="small" @click="loadUsage">刷新</el-button>
          </div>
          <template v-if="usage">
            <el-row :gutter="12" class="stats">
              <el-col :span="6"><div class="stat"><div class="num">{{ (usage.toolCalls as any).total }}</div><div class="lbl">工具调用总数</div></div></el-col>
              <el-col :span="6"><div class="stat"><div class="num">{{ (usage.toolCalls as any).successRate }}%</div><div class="lbl">成功率</div></div></el-col>
              <el-col :span="6"><div class="stat"><div class="num">{{ (usage.toolCalls as any).failure }}</div><div class="lbl">失败数</div></div></el-col>
              <el-col :span="6"><div class="stat"><div class="num">{{ (usage.toolCalls as any).avgDurationMs }} ms</div><div class="lbl">平均耗时</div></div></el-col>
            </el-row>
            <el-row :gutter="12">
              <el-col :span="8">
                <el-card shadow="never" header="按工具">
                  <el-table :data="usage.byTool as CountRow[]" size="small" border>
                    <el-table-column prop="toolCode" label="工具" min-width="140" />
                    <el-table-column prop="count" label="次数" width="80" />
                  </el-table>
                </el-card>
              </el-col>
              <el-col :span="8">
                <el-card shadow="never" header="按渠道">
                  <el-table :data="usage.byChannel as CountRow[]" size="small" border>
                    <el-table-column prop="channel" label="渠道" min-width="120" />
                    <el-table-column prop="count" label="次数" width="80" />
                  </el-table>
                </el-card>
              </el-col>
              <el-col :span="8">
                <el-card shadow="never" header="按天">
                  <el-table :data="usage.byDay as CountRow[]" size="small" border max-height="280">
                    <el-table-column prop="day" label="日期" min-width="120" />
                    <el-table-column prop="count" label="次数" width="80" />
                  </el-table>
                </el-card>
              </el-col>
            </el-row>
            <el-row :gutter="12" class="row-gap">
              <el-col :span="12">
                <el-card shadow="never" :header="`会话（${(usage.sessions as any).total}）`">
                  <el-table :data="(usage.sessions as any).byChannel as CountRow[]" size="small" border>
                    <el-table-column prop="channel" label="渠道" min-width="120" />
                    <el-table-column prop="count" label="会话数" width="100" />
                  </el-table>
                </el-card>
              </el-col>
              <el-col :span="12">
                <el-card shadow="never" :header="`消息（${(usage.messages as any).total}）`">
                  <el-table :data="(usage.messages as any).byRole as CountRow[]" size="small" border>
                    <el-table-column prop="role" label="角色" min-width="120" />
                    <el-table-column prop="count" label="消息数" width="100" />
                  </el-table>
                </el-card>
              </el-col>
            </el-row>
          </template>
        </el-tab-pane>

        <el-tab-pane label="数据看板" name="dashboard">
          <div class="toolbar">
            <el-button type="primary" size="small" @click="loadDashboard">刷新</el-button>
            <span class="muted">各模块计数（取自库表实时统计）</span>
          </div>
          <template v-if="dashboard">
            <el-row :gutter="12" class="stats">
              <el-col v-for="(label, key) in COUNT_LABEL" :key="key" :span="4">
                <div class="stat small">
                  <div class="num">{{ (dashboard.counts as any)[key] ?? 0 }}</div>
                  <div class="lbl">{{ label }}</div>
                </div>
              </el-col>
            </el-row>
            <el-card shadow="never" header="最近工具调用" class="row-gap">
              <el-table :data="dashboard.recentToolCalls as CountRow[]" size="small" border>
                <el-table-column prop="createTime" label="时间" width="180" />
                <el-table-column prop="toolCode" label="工具" min-width="150" />
                <el-table-column prop="channel" label="渠道" width="90" />
                <el-table-column label="结果" width="90">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.success === 1 ? 'success' : 'danger'">{{ row.success === 1 ? '成功' : '失败' }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="durationMs" label="耗时(ms)" width="100" />
              </el-table>
            </el-card>
          </template>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<style scoped>
.view { display: flex; flex-direction: column; gap: 12px; }
.head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.head .el-button { margin-left: auto; }
.muted { color: #909399; font-size: 12px; }
.toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; flex-wrap: wrap; }
.stats { margin-bottom: 12px; }
.stat { border: 1px solid #ebeef5; border-radius: 4px; padding: 12px; text-align: center; }
.stat.small { padding: 8px; }
.stat .num { font-size: 20px; font-weight: 600; color: #303133; }
.stat.small .num { font-size: 16px; }
.stat .lbl { color: #909399; font-size: 12px; margin-top: 4px; }
.row-gap { margin-top: 12px; }
</style>
