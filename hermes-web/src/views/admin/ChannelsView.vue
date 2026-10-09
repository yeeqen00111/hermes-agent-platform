<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'

interface Channel {
  id?: number
  channelCode: string
  channelType: string
  name: string
  appId?: string
  appSecretRef?: string
  /** 渠道扩展配置 JSON（钉钉/企微 agentId 等） */
  config?: string
  enabled: number
  status?: string
  errorMessage?: string
  ownerInstance?: string
  heartbeatTime?: string
}

const TYPES = ['feishu', 'dingtalk', 'wecom', 'web', 'api']

const channels = ref<Channel[]>([])
const loading = ref(false)

const dialogVisible = ref(false)
const saving = ref(false)
const form = reactive<Channel>({ channelCode: '', channelType: 'feishu', name: '', enabled: 1 })

async function load() {
  loading.value = true
  try {
    const { data } = await http.get<Channel[]>('/channels')
    channels.value = data ?? []
  } catch (e) {
    ElMessage.error('加载渠道失败：' + (e as Error).message)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { id: undefined, channelCode: '', channelType: 'feishu', name: '', appId: '', appSecretRef: '', config: '', enabled: 1 })
  dialogVisible.value = true
}

function openEdit(row: Channel) {
  Object.assign(form, row)
  dialogVisible.value = true
}

async function save() {
  if (!form.channelCode || !form.name) {
    ElMessage.warning('channelCode 与 name 必填')
    return
  }
  saving.value = true
  try {
    await http.post('/channels', { ...form })
    ElMessage.success('已保存')
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error('保存失败：' + (e as Error).message)
  } finally {
    saving.value = false
  }
}

async function heartbeat(row: Channel) {
  await http.post(`/channels/${row.channelCode}/heartbeat`)
  ElMessage.success('已上报心跳')
  await load()
}

async function remove(row: Channel) {
  await ElMessageBox.confirm(`删除渠道 ${row.channelCode}？`, '提示', { type: 'warning' })
  await http.delete(`/channels/${row.channelCode}`)
  await load()
}

onMounted(load)
</script>

<template>
  <div class="view">
    <el-card shadow="never">
      <template #header>
        <div class="head">
          <b>Agent · 渠道</b>
          <span class="muted">飞书/钉钉/企微/Web 接入；心跳与配对用户</span>
          <el-button type="primary" size="small" @click="openCreate">新建渠道</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="channels" border stripe size="small">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="channelCode" label="编码" width="150" />
        <el-table-column prop="channelType" label="类型" width="110" />
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column prop="appId" label="AppId" min-width="150" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="heartbeatTime" label="心跳时间" width="170" />
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="success" size="small" @click="heartbeat(row)">心跳</el-button>
            <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑渠道' : '新建渠道'" width="560px">
      <el-form label-width="100px">
        <el-form-item label="编码" required><el-input v-model="form.channelCode" :disabled="!!form.id" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.channelType" style="width: 100%">
            <el-option v-for="t in TYPES" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="AppId"><el-input v-model="form.appId" /></el-form-item>
        <el-form-item label="密钥引用"><el-input v-model="form.appSecretRef" placeholder="环境变量名（不存明文）" /></el-form-item>
        <el-form-item label="扩展配置">
          <el-input
            v-model="form.config"
            type="textarea"
            :rows="2"
            placeholder='JSON，如钉钉/企微 {"agentId":"123"}'
          />
        </el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
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
