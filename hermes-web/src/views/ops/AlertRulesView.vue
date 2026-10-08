<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'

interface AlertRule {
  id?: number
  code: string
  name: string
  projectName?: string
  ruleType: string
  priority?: number
  matchPattern?: string
  levelFilter?: string
  channelCode?: string
  recipient?: string
  enabled: number
  remark?: string
}

const tab = ref('ALERT')
const loading = ref(false)
const rules = ref<AlertRule[]>([])

const dialog = ref(false)
const saving = ref(false)

const empty = (type: string): AlertRule => ({
  code: '',
  name: '',
  projectName: '',
  ruleType: type,
  matchPattern: '',
  levelFilter: '',
  channelCode: '',
  recipient: '',
  enabled: 1,
  remark: '',
})
const form = reactive<AlertRule>(empty('ALERT'))

const visible = computed(() => rules.value.filter((r) => r.ruleType === tab.value))

const TYPE_LABEL: Record<string, string> = {
  ALERT: '告警规则（该告警）',
  WHITELIST: '告警白名单（不告警）',
  PROMOTE: '提级告警',
}

async function load() {
  loading.value = true
  try {
    const { data } = await http.get<AlertRule[]>('/ops/alert/rules')
    rules.value = data ?? []
  } catch (e) {
    ElMessage.error('加载告警规则失败：' + (e as Error).message)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, empty(tab.value))
  dialog.value = true
}

function openEdit(row: AlertRule) {
  Object.assign(form, empty(row.ruleType), row)
  dialog.value = true
}

async function save() {
  if (!form.code || !form.name) {
    ElMessage.warning('编码与名称必填')
    return
  }
  saving.value = true
  try {
    await http.post('/ops/alert/rules', { ...form })
    ElMessage.success('已保存')
    dialog.value = false
    await load()
  } catch (e) {
    ElMessage.error('保存失败：' + (e as Error).message)
  } finally {
    saving.value = false
  }
}

async function remove(row: AlertRule) {
  await ElMessageBox.confirm(`删除${TYPE_LABEL[row.ruleType] ?? '规则'} ${row.code}？`, '提示', { type: 'warning' })
  await http.delete(`/ops/alert/rules/${row.id}`)
  await load()
}

onMounted(load)
</script>

<template>
  <div class="view">
    <el-card shadow="never">
      <template #header>
        <div class="head">
          <b>业务层 · 智能运维 · 日志告警规则</b>
          <span class="muted">分项目一套配置；固化优先级：① 告警规则 → ② 白名单 → ③ 提级告警；去向=通知通道 + 接收人</span>
        </div>
      </template>

      <el-tabs v-model="tab">
        <el-tab-pane v-for="t in ['ALERT', 'WHITELIST', 'PROMOTE']" :key="t" :label="TYPE_LABEL[t]" :name="t">
          <div class="toolbar">
            <span class="muted">
              {{ t === 'ALERT' ? '命中即告警（发送到指定通道/接收人）' : t === 'WHITELIST' ? '命中即拦截，不发送告警' : '命中即提级告警（优先级高于普通告警）' }}
            </span>
            <el-button type="primary" size="small" @click="openCreate">新建</el-button>
          </div>
          <el-table v-loading="loading" :data="visible" border stripe size="small">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="code" label="编码" width="140" />
            <el-table-column prop="name" label="名称" min-width="130" />
            <el-table-column prop="projectName" label="项目" min-width="120">
              <template #default="{ row }">{{ row.projectName || '全部项目' }}</template>
            </el-table-column>
            <el-table-column prop="priority" label="优先级" width="80" />
            <el-table-column prop="levelFilter" label="级别过滤" width="110">
              <template #default="{ row }">{{ row.levelFilter || '不限' }}</template>
            </el-table-column>
            <el-table-column prop="matchPattern" label="内容匹配(正则)" min-width="160" show-overflow-tooltip />
            <el-table-column prop="channelCode" label="去向通道" width="120" />
            <el-table-column prop="recipient" label="接收人" min-width="140" show-overflow-tooltip />
            <el-table-column label="启用" width="80">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="dialog" :title="(form.id ? '编辑' : '新建') + (TYPE_LABEL[form.ruleType] ?? '规则')" width="600px">
      <el-form label-width="120px">
        <el-form-item label="编码" required><el-input v-model="form.code" :disabled="!!form.id" placeholder="如 order-error-alert" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="form.ruleType">
            <el-radio value="ALERT">告警规则</el-radio>
            <el-radio value="WHITELIST">白名单</el-radio>
            <el-radio value="PROMOTE">提级告警</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="所属项目"><el-input v-model="form.projectName" placeholder="空=全部项目" /></el-form-item>
        <el-form-item label="级别过滤"><el-input v-model="form.levelFilter" placeholder="如 ERROR,FATAL（空=不限）" /></el-form-item>
        <el-form-item label="内容匹配"><el-input v-model="form.matchPattern" placeholder="正则，空=全部；如 OutOfMemory" /></el-form-item>
        <el-form-item label="去向通道"><el-input v-model="form.channelCode" placeholder="通知通道编码（渠道，如 feishu-ops）" /></el-form-item>
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
.toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.toolbar .el-button { margin-left: auto; }
.muted { color: #909399; font-size: 12px; }
</style>
