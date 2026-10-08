<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'

interface LogRetention {
  id?: number
  dataType: string
  retentionDays: number
  enabled: number
  remark?: string
}

const loading = ref(false)
const saving = ref('')
const rows = ref<LogRetention[]>([])
const cleanResult = ref('')

const TYPE_LABEL: Record<string, string> = {
  FULL: '全量日志',
  ALERT: '告警日志',
  PROMOTE: '提级告警日志',
}

async function load() {
  loading.value = true
  try {
    const { data } = await http.get<LogRetention[]>('/ops/alert/retention')
    rows.value = data ?? []
  } catch (e) {
    ElMessage.error('加载保留策略失败：' + (e as Error).message)
  } finally {
    loading.value = false
  }
}

async function save(row: LogRetention) {
  saving.value = row.dataType
  try {
    await http.post('/ops/alert/retention', { ...row })
    ElMessage.success('已保存')
    await load()
  } catch (e) {
    ElMessage.error('保存失败：' + (e as Error).message)
  } finally {
    saving.value = ''
  }
}

async function cleanNow() {
  try {
    const { data } = await http.post('/ops/alert/clean')
    cleanResult.value = JSON.stringify(data, null, 2)
    ElMessage.success('已按策略清理')
  } catch (e) {
    ElMessage.error('清理失败：' + (e as Error).message)
  }
}

onMounted(load)
</script>

<template>
  <div class="view">
    <el-card shadow="never">
      <template #header>
        <div class="head">
          <b>业务层 · 智能运维 · 日志清理（分级保留 3/7/7 天）</b>
          <span class="muted">全量 3 天 / 告警 7 天 / 提级告警 7 天；定时任务执行，亦可手动触发</span>
          <el-button size="small" type="warning" @click="cleanNow">立即清理</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="rows" border stripe size="small">
        <el-table-column prop="dataType" label="数据类型" width="150">
          <template #default="{ row }">{{ TYPE_LABEL[row.dataType] ?? row.dataType }}</template>
        </el-table-column>
        <el-table-column label="保留天数" width="180">
          <template #default="{ row }">
            <el-input-number v-model="row.retentionDays" :min="1" :max="365" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="启用" width="100">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" :active-value="1" :inactive-value="0" />
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="说明" min-width="200" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" :loading="saving === row.dataType" @click="save(row)">保存</el-button>
          </template>
        </el-table-column>
      </el-table>
      <p class="muted note">说明：全量日志不落本表（由日志采集侧承载），此处清理作用于告警/提级告警记录。</p>
      <pre v-if="cleanResult" class="result">{{ cleanResult }}</pre>
    </el-card>
  </div>
</template>

<style scoped>
.view { display: flex; flex-direction: column; gap: 12px; }
.head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.head .el-button { margin-left: auto; }
.muted { color: #909399; font-size: 12px; }
.note { margin-top: 10px; }
.result { background: #f5f7fa; padding: 10px; border-radius: 4px; font-size: 12px; }
</style>
