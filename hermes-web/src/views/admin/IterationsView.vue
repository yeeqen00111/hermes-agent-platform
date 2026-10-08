<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'

interface AgentProfile {
  id?: number
  agentCode: string
  name: string
  status?: string
  executionMode?: string
  currentVersion?: number
  grayVersion?: number | null
  grayRatio?: number | null
}

interface AgentVersion {
  version: number
  publishBy?: string
  publishTime?: string
}

const agents = ref<AgentProfile[]>([])
const loading = ref(false)

const verVisible = ref(false)
const versions = ref<AgentVersion[]>([])
const verAgent = ref<AgentProfile | null>(null)

const grayVisible = ref(false)
const grayAgent = ref<AgentProfile | null>(null)
const grayForm = reactive<{ version: number | null; ratio: number }>({ version: null, ratio: 0 })
const graySaving = ref(false)

async function load() {
  loading.value = true
  try {
    const { data } = await http.get<AgentProfile[]>('/agent-profiles')
    agents.value = data ?? []
  } catch (e) {
    ElMessage.error('加载迭代列表失败：' + (e as Error).message)
  } finally {
    loading.value = false
  }
}

async function publish(row: AgentProfile) {
  if (!row.id) return
  const { data } = await http.post<{ success: boolean; version?: number }>(`/agent-profiles/${row.id}/publish`)
  if (data?.success) ElMessage.success('已发布 v' + data.version)
  await load()
}

async function openVersions(row: AgentProfile) {
  verAgent.value = row
  verVisible.value = true
  if (!row.id) return
  const { data } = await http.get<{ success: boolean; data: AgentVersion[] }>(`/agent-profiles/${row.id}/versions`)
  versions.value = data?.success ? data.data ?? [] : []
}

async function rollback(v: number) {
  if (!verAgent.value?.id) return
  await http.post(`/agent-profiles/${verAgent.value.id}/rollback`, null, { params: { version: v } })
  ElMessage.success('已回滚到 v' + v)
  verVisible.value = false
  await load()
}

async function openGray(row: AgentProfile) {
  grayAgent.value = row
  grayForm.version = row.grayVersion ?? row.currentVersion ?? null
  grayForm.ratio = row.grayRatio ?? 0
  grayVisible.value = true
  if (row.id) {
    const { data } = await http.get<{ success: boolean; data: AgentVersion[] }>(`/agent-profiles/${row.id}/versions`)
    versions.value = data?.success ? data.data ?? [] : []
  }
}

async function saveGray() {
  if (!grayAgent.value?.id) return
  if (grayForm.ratio > 0 && grayForm.version == null) {
    ElMessage.warning('开启灰度必须选择目标版本')
    return
  }
  graySaving.value = true
  try {
    const { data } = await http.post<{ success: boolean; message?: string }>(
      `/agent-profiles/${grayAgent.value.id}/gray`,
      { version: grayForm.ratio > 0 ? grayForm.version : null, ratio: grayForm.ratio },
    )
    if (data?.success) {
      ElMessage.success(grayForm.ratio > 0 ? `灰度已开启：${grayForm.ratio}% → v${grayForm.version}` : '灰度已关闭')
      grayVisible.value = false
      await load()
    } else {
      ElMessage.error(data?.message ?? '灰度设置失败')
    }
  } catch (e) {
    ElMessage.error('灰度设置失败：' + (e as Error).message)
  } finally {
    graySaving.value = false
  }
}

function grayTag(row: AgentProfile) {
  if (!row.grayVersion || !row.grayRatio) return '未开启'
  return `v${row.grayVersion} · ${row.grayRatio}%`
}

onMounted(load)
</script>

<template>
  <div class="view">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="迭代管理 = 人格包版本（发布写不可变快照 + 移版本指针）；灰度把新会话按比例指向目标版本，会话中途不切版本（ADR-009 / 智能体平台设计 §8.2）"
    />

    <el-card shadow="never">
      <template #header>
        <div class="head">
          <b>系统层 · 迭代管理（Agent 版本 / 灰度）</b>
          <span class="muted">共 {{ agents.length }} 个智能体</span>
          <el-button size="small" @click="load">刷新</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="agents" border stripe size="small">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="agentCode" label="编码" width="160" />
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column prop="executionMode" label="执行模式" width="120" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="currentVersion" label="当前发布" width="90">
          <template #default="{ row }">v{{ row.currentVersion ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="灰度" width="130">
          <template #default="{ row }">
            <el-tag size="small" :type="row.grayVersion && row.grayRatio ? 'warning' : 'info'">
              {{ grayTag(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="publish(row)">发布</el-button>
            <el-button link type="info" size="small" @click="openVersions(row)">版本历史</el-button>
            <el-button link type="warning" size="small" @click="openGray(row)">灰度</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="verVisible" :title="`版本历史 · ${verAgent?.name ?? ''}`" width="520px">
      <el-table :data="versions" border size="small">
        <el-table-column prop="version" label="版本" width="80">
          <template #default="{ row }">v{{ row.version }}</template>
        </el-table-column>
        <el-table-column prop="publishBy" label="发布人" width="120" />
        <el-table-column prop="publishTime" label="发布时间" min-width="170" />
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button link type="danger" size="small" @click="rollback(row.version)">回滚</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="grayVisible" :title="`灰度 · ${grayAgent?.name ?? ''}`" width="520px">
      <el-form label-width="110px">
        <el-form-item label="当前发布">
          <span>v{{ grayAgent?.currentVersion ?? '-' }}</span>
        </el-form-item>
        <el-form-item label="目标版本">
          <el-select v-model="grayForm.version" :disabled="grayForm.ratio <= 0" style="width: 100%">
            <el-option v-for="v in versions" :key="v.version" :label="`v${v.version}`" :value="v.version" />
          </el-select>
        </el-form-item>
        <el-form-item label="灰度比例(%)">
          <el-input-number v-model="grayForm.ratio" :min="0" :max="100" :step="5" />
        </el-form-item>
      </el-form>
      <div class="muted">
        比例 0 = 关闭灰度（全部新会话走当前发布版本）；比例 &gt;0 时，新会话按该比例绑定目标版本。
        已建立的会话保持原版本不变。
      </div>
      <template #footer>
        <el-button @click="grayVisible = false">取消</el-button>
        <el-button type="primary" :loading="graySaving" @click="saveGray">保存</el-button>
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
}
.head .el-button {
  margin-left: auto;
}
.muted {
  color: #909399;
  font-size: 12px;
}
</style>
