<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'

const loading = ref(false)
const overview = ref<Record<string, unknown> | null>(null)

async function load() {
  loading.value = true
  try {
    const { data } = await http.get<Record<string, unknown>>('/dashboard/overview', {
      params: { windowHours: 12 },
    })
    overview.value = data
  } catch (e) {
    ElMessage.error('加载看板失败：' + (e as Error).message)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="dashboard">
    <el-alert
      type="info"
      :closable="false"
      title="看板监控数据面已就绪（GET /api/dashboard/overview）。白板四类告警与半天报表-AI 由平台侧定时任务推送，图表可视化后续迭代。"
    />
    <el-card class="json-card" shadow="never">
      <template #header><b>概览数据（windowHours=12）</b></template>
      <pre class="json">{{ JSON.stringify(overview, null, 2) }}</pre>
    </el-card>
  </div>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.json {
  margin: 0;
  font-size: 12px;
  max-height: 60vh;
  overflow: auto;
}
</style>
