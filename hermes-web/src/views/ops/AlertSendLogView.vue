<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'

interface NotifyLog {
  id: number
  channelCode?: string
  channelType?: string
  recipient?: string
  title?: string
  content?: string
  status?: string
  error?: string
  createTime?: string
}

const loading = ref(false)
const logs = ref<NotifyLog[]>([])
const limit = ref(200)

const STATUS_TAG: Record<string, string> = { SUCCESS: 'success', FAILED: 'danger', NO_CHANNEL: 'warning' }

async function load() {
  loading.value = true
  try {
    const { data } = await http.get<NotifyLog[]>('/admin/notify/logs', { params: { limit: limit.value } })
    logs.value = data ?? []
  } catch (e) {
    ElMessage.error('加载告警发送日志失败：' + (e as Error).message)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="view">
    <el-card shadow="never">
      <template #header>
        <div class="head">
          <b>业务层 · 智能运维 · 告警发送日志</b>
          <span class="muted">告警/提级的发送历史（飞书 / 邮件）；数据来自通知网关 ai_notify_log</span>
          <el-input-number v-model="limit" :min="20" :max="500" :step="20" size="small" />
          <el-button size="small" @click="load">刷新</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="logs" border stripe size="small">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="createTime" label="发送时间" min-width="170" />
        <el-table-column prop="channelCode" label="通道" width="130" />
        <el-table-column prop="channelType" label="类型" width="90" />
        <el-table-column prop="recipient" label="接收人" min-width="150" show-overflow-tooltip />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="STATUS_TAG[row.status] ?? 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="error" label="错误" min-width="160" show-overflow-tooltip />
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.view { display: flex; flex-direction: column; gap: 12px; }
.head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.head .el-button { margin-left: auto; }
.muted { color: #909399; font-size: 12px; }
</style>
