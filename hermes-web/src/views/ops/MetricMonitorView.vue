<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'

interface BizMetric {
  id?: number
  code: string
  name: string
  metricPoint?: string
  systemName?: string
  metricType?: string
  unit?: string
  sourceRef?: string
  thresholdOp?: string
  thresholdValue?: number | null
  channelCode?: string
  recipient?: string
  enabled: number
  remark?: string
}

interface MonitorRow extends BizMetric {
  latestValue?: number | null
  latestTime?: string
  breached?: boolean
}

const loading = ref(false)
const rows = ref<MonitorRow[]>([])
const metrics = ref<BizMetric[]>([])

const dialog = ref(false)
const saving = ref(false)
const empty = (): BizMetric => ({
  code: '',
  name: '',
  metricPoint: '',
  systemName: '',
  metricType: '',
  unit: '',
  sourceRef: '',
  thresholdOp: '>',
  thresholdValue: null,
  channelCode: '',
  recipient: '',
  enabled: 1,
  remark: '',
})
const form = reactive<BizMetric>(empty())

const inject = reactive({ code: '', value: 0 })
const injectResult = ref('')

async function load() {
  loading.value = true
  try {
    const [monitorRes, metricsRes] = await Promise.all([
      http.get<MonitorRow[]>('/ops/metric/monitor'),
      http.get<BizMetric[]>('/ops/metric/metrics'),
    ])
    rows.value = monitorRes.data ?? []
    metrics.value = metricsRes.data ?? []
  } catch (e) {
    ElMessage.error('加载业务指标失败：' + (e as Error).message)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, empty())
  dialog.value = true
}

function openEdit(row: MonitorRow) {
  Object.assign(form, empty(), row)
  dialog.value = true
}

async function save() {
  if (!form.code || !form.name) {
    ElMessage.warning('编码与名称必填')
    return
  }
  saving.value = true
  try {
    await http.post('/ops/metric/metrics', { ...form })
    ElMessage.success('已保存')
    dialog.value = false
    await load()
  } catch (e) {
    ElMessage.error('保存失败：' + (e as Error).message)
  } finally {
    saving.value = false
  }
}

async function remove(row: MonitorRow) {
  await ElMessageBox.confirm(`删除指标 ${row.code}？`, '提示', { type: 'warning' })
  await http.delete(`/ops/metric/metrics/${row.id}`)
  await load()
}

async function runInject() {
  if (!inject.code) {
    ElMessage.warning('请选择或填写指标编码')
    return
  }
  try {
    const { data } = await http.post('/ops/metric/ingest', { code: inject.code, value: inject.value })
    injectResult.value = JSON.stringify(data, null, 2)
    if (data.success) {
      ElMessage[data.breached ? 'warning' : 'success'](data.breached ? '命中阈值，已告警' : '采样正常')
      await load()
    } else {
      ElMessage.error(String(data.message ?? '采样失败'))
    }
  } catch (e) {
    ElMessage.error('采样失败：' + (e as Error).message)
  }
}

onMounted(load)
</script>

<template>
  <div class="view">
    <el-card shadow="never">
      <template #header>
        <div class="head">
          <b>业务层 · 智能运维 · 业务指标监控</b>
          <span class="muted">业务指标埋点 / 所属系统 / 指标类型；可配阈值，命中即告警（落告警记录 + 通知）</span>
          <el-button type="primary" size="small" @click="openCreate">新建指标</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="rows" border stripe size="small">
        <el-table-column prop="code" label="编码" width="140" />
        <el-table-column prop="name" label="名称" min-width="130" />
        <el-table-column prop="metricPoint" label="业务指标埋点" min-width="160" show-overflow-tooltip />
        <el-table-column prop="systemName" label="所属系统" width="130" />
        <el-table-column prop="metricType" label="指标类型" width="100" />
        <el-table-column label="最新值" width="110">
          <template #default="{ row }">
            <span :class="{ breach: row.breached }">{{ row.latestValue ?? '—' }}{{ row.unit ?? '' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="latestTime" label="采样时间" min-width="170" />
        <el-table-column label="阈值" width="110">
          <template #default="{ row }">{{ row.thresholdValue == null ? '—' : `${row.thresholdOp} ${row.thresholdValue}` }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.breached" type="danger" size="small">超阈值</el-tag>
            <el-tag v-else-if="row.latestValue != null" type="success" size="small">正常</el-tag>
            <el-tag v-else type="info" size="small">无采样</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never">
      <template #header>采样联调（生产由埋点 / SQL / 接口推送同一入口）</template>
      <el-form label-width="90px" class="inline-form">
        <el-form-item label="指标">
          <el-select v-model="inject.code" filterable allow-create placeholder="选择或输入编码" style="width: 260px">
            <el-option v-for="m in metrics" :key="m.code" :label="`${m.name} (${m.code})`" :value="m.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="采样值">
          <el-input-number v-model="inject.value" :precision="2" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="runInject">注入采样</el-button>
        </el-form-item>
      </el-form>
      <pre v-if="injectResult" class="result">{{ injectResult }}</pre>
    </el-card>

    <el-dialog v-model="dialog" :title="form.id ? '编辑指标' : '新建指标'" width="600px">
      <el-form label-width="110px">
        <el-form-item label="编码" required><el-input v-model="form.code" :disabled="!!form.id" placeholder="如 order-rt" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="指标埋点"><el-input v-model="form.metricPoint" placeholder="埋点点位标识" /></el-form-item>
        <el-form-item label="所属系统"><el-input v-model="form.systemName" placeholder="如 订单系统" /></el-form-item>
        <el-form-item label="指标类型"><el-input v-model="form.metricType" placeholder="如 耗时 / 计数 / 比率" /></el-form-item>
        <el-form-item label="单位"><el-input v-model="form.unit" placeholder="如 ms / 次 / %" /></el-form-item>
        <el-form-item label="取数引用"><el-input v-model="form.sourceRef" placeholder="埋点 key / SQL / 接口" /></el-form-item>
        <el-form-item label="告警阈值">
          <el-select v-model="form.thresholdOp" style="width: 90px">
            <el-option v-for="op in ['>', '>=', '<', '<=', '==']" :key="op" :label="op" :value="op" />
          </el-select>
          <el-input-number v-model="form.thresholdValue" :precision="2" style="margin-left: 8px" />
        </el-form-item>
        <el-form-item label="去向通道"><el-input v-model="form.channelCode" placeholder="通知通道编码" /></el-form-item>
        <el-form-item label="接收人"><el-input v-model="form.recipient" placeholder="邮箱 / 飞书账号" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.view { display: flex; flex-direction: column; gap: 12px; }
.head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.head .el-button { margin-left: auto; }
.muted { color: #909399; font-size: 12px; }
.breach { color: #f56c6c; font-weight: 600; }
.inline-form { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.inline-form :deep(.el-form-item) { margin-bottom: 0; }
.result { background: #f5f7fa; padding: 10px; border-radius: 4px; font-size: 12px; margin-top: 10px; }
</style>
