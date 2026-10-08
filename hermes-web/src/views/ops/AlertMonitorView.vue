<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'

interface IngestStat {
  projectName: string
  lastIngestTime?: string
  totalIngested?: number
  totalAlert?: number
  totalPromote?: number
  totalSuppress?: number
}

interface DimRow {
  count: number
  [key: string]: unknown
}

interface HistoryRow {
  id: number
  ruleCode?: string
  projectName?: string
  systemName?: string
  serverName?: string
  logLevel?: string
  alertType?: string
  status?: string
  content?: string
  logTime?: string
  createTime?: string
}

const loading = ref(false)
const stats = ref<IngestStat[]>([])
const byRule = ref<DimRow[]>([])
const bySystem = ref<DimRow[]>([])
const byServer = ref<DimRow[]>([])
const byLevel = ref<DimRow[]>([])
const history = ref<HistoryRow[]>([])

const injecting = ref(false)
const inject = reactive({ channelCode: '', raw: '{"app":"order","level":"ERROR","msg":"OutOfMemory on order-api","service":"order-api"}' })
const injectResult = ref('')

const STATUS_TAG: Record<string, string> = { SENT: 'success', FAILED: 'danger', NO_CHANNEL: 'warning', SUPPRESSED: 'info' }

async function load() {
  loading.value = true
  try {
    const { data } = await http.get('/ops/alert/monitor')
    stats.value = data.stats ?? []
    byRule.value = data.byRule ?? []
    bySystem.value = data.bySystem ?? []
    byServer.value = data.byServer ?? []
    byLevel.value = data.byLevel ?? []
    history.value = data.history ?? []
  } catch (e) {
    ElMessage.error('加载告警监控失败：' + (e as Error).message)
  } finally {
    loading.value = false
  }
}

async function runInject() {
  injecting.value = true
  try {
    const { data } = await http.post('/ops/alert/ingest', { channelCode: inject.channelCode, raw: inject.raw })
    injectResult.value = JSON.stringify(data, null, 2)
    if (data.success) {
      ElMessage.success(`固化完成：${data.decision}`)
      await load()
    } else {
      ElMessage.warning(String(data.message ?? '固化失败'))
    }
  } catch (e) {
    ElMessage.error('注入失败：' + (e as Error).message)
  } finally {
    injecting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="view">
    <el-card shadow="never">
      <template #header>
        <div class="head">
          <b>业务层 · 智能运维 · 告警监控</b>
          <span class="muted">维度：按系统 / 服务器 / 日志级别；上次接收过滤时间；告警历史记录；告警触发次数</span>
          <el-button size="small" @click="load">刷新</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="stats" border stripe size="small">
        <el-table-column prop="projectName" label="项目（系统）" min-width="140" />
        <el-table-column prop="lastIngestTime" label="上次接收过滤时间" min-width="180" />
        <el-table-column prop="totalIngested" label="累计接收" width="100" />
        <el-table-column prop="totalAlert" label="告警次数" width="100" />
        <el-table-column prop="totalPromote" label="提级次数" width="100" />
        <el-table-column prop="totalSuppress" label="白名单拦截" width="120" />
      </el-table>
    </el-card>

    <el-row :gutter="12">
      <el-col :span="8">
        <el-card shadow="never" header="触发次数 · 按规则">
          <el-table :data="byRule" size="small" border>
            <el-table-column prop="rule" label="规则 · 类型" min-width="150" />
            <el-table-column prop="count" label="次数" width="80" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" header="触发次数 · 按系统">
          <el-table :data="bySystem" size="small" border>
            <el-table-column prop="systemName" label="系统" min-width="120" />
            <el-table-column prop="count" label="次数" width="80" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" header="触发次数 · 按级别">
          <el-table :data="byLevel" size="small" border>
            <el-table-column prop="logLevel" label="级别" min-width="120" />
            <el-table-column prop="count" label="次数" width="80" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <template #header>告警历史记录</template>
      <el-table v-loading="loading" :data="history" border stripe size="small">
        <el-table-column prop="createTime" label="记录时间" min-width="170" />
        <el-table-column prop="systemName" label="系统" width="110" />
        <el-table-column prop="serverName" label="服务器" width="120" />
        <el-table-column prop="logLevel" label="级别" width="80" />
        <el-table-column prop="ruleCode" label="命中规则" width="150" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.alertType === 'PROMOTE' ? 'danger' : row.alertType === 'SUPPRESSED' ? 'info' : 'warning'">
              {{ row.alertType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="STATUS_TAG[row.status] ?? 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="内容" min-width="220" show-overflow-tooltip />
      </el-table>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="head">
          <span>固化引擎联调（生产由 kafka 消费同一引擎，dev 由此注入原始日志）</span>
        </div>
      </template>
      <el-form label-width="90px">
        <el-form-item label="采集通道"><el-input v-model="inject.channelCode" placeholder="可空（自动选解析规则）" style="max-width: 320px" /></el-form-item>
        <el-form-item label="原始日志"><el-input v-model="inject.raw" type="textarea" :rows="3" /></el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="injecting" @click="runInject">注入并固化</el-button>
        </el-form-item>
      </el-form>
      <pre v-if="injectResult" class="result">{{ injectResult }}</pre>
    </el-card>
  </div>
</template>

<style scoped>
.view { display: flex; flex-direction: column; gap: 12px; }
.head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.head .el-button { margin-left: auto; }
.muted { color: #909399; font-size: 12px; }
.result { background: #f5f7fa; padding: 10px; border-radius: 4px; font-size: 12px; max-height: 240px; overflow: auto; }
</style>
