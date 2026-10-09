<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'

interface OpsGrant {
  id?: number
  code: string
  name: string
  agentCode: string
  grantType: string
  resourceRef?: string
  permission?: string
  enabled: number
  remark?: string
}

interface AgentProfile {
  agentCode: string
  name?: string
}

const tab = ref('REPO')
const loading = ref(false)
const grants = ref<OpsGrant[]>([])
const agents = ref<AgentProfile[]>([])

const dialog = ref(false)
const saving = ref(false)
const empty = (type: string): OpsGrant => ({
  code: '',
  name: '',
  agentCode: '',
  grantType: type,
  resourceRef: '',
  permission: 'READ',
  enabled: 1,
  remark: '',
})
const form = reactive<OpsGrant>(empty('REPO'))

const scopeAgent = ref('')
const scope = ref<{ repo: string[]; nacos: string[]; log: string[] }>({ repo: [], nacos: [], log: [] })

const visible = computed(() => grants.value.filter((g) => g.grantType === tab.value))
const TYPE_LABEL: Record<string, string> = {
  REPO: '代码仓库授权',
  NACOS: 'nacos 配置授权',
  LOG: '日志授权',
}
const TYPE_HINT: Record<string, string> = {
  REPO: '运维助手可以查看哪些代码仓库',
  NACOS: '运维助手可以查看哪些 nacos 配置',
  LOG: '运维助手可以查看哪些日志',
}

async function load() {
  loading.value = true
  try {
    const [grantRes, agentRes] = await Promise.all([
      http.get<OpsGrant[]>('/ops/grant/grants'),
      http.get<AgentProfile[]>('/agent-profiles'),
    ])
    grants.value = grantRes.data ?? []
    agents.value = agentRes.data ?? []
    if (!scopeAgent.value && agents.value.length) scopeAgent.value = agents.value[0].agentCode
  } catch (e) {
    ElMessage.error('加载授权失败：' + (e as Error).message)
  } finally {
    loading.value = false
  }
}

async function loadScope() {
  if (!scopeAgent.value) return
  try {
    const { data } = await http.get('/ops/grant/scope', { params: { agentCode: scopeAgent.value } })
    scope.value = { repo: data.repo ?? [], nacos: data.nacos ?? [], log: data.log ?? [] }
  } catch (e) {
    ElMessage.error('加载可见范围失败：' + (e as Error).message)
  }
}

function openCreate() {
  Object.assign(form, empty(tab.value))
  dialog.value = true
}

function openEdit(row: OpsGrant) {
  Object.assign(form, empty(row.grantType), row)
  dialog.value = true
}

async function save() {
  if (!form.code || !form.name || !form.agentCode) {
    ElMessage.warning('编码、名称、授权助手必填')
    return
  }
  saving.value = true
  try {
    await http.post('/ops/grant/grants', { ...form })
    ElMessage.success('已保存')
    dialog.value = false
    await load()
    await loadScope()
  } catch (e) {
    ElMessage.error('保存失败：' + (e as Error).message)
  } finally {
    saving.value = false
  }
}

async function remove(row: OpsGrant) {
  await ElMessageBox.confirm(`删除授权 ${row.code}？`, '提示', { type: 'warning' })
  await http.delete(`/ops/grant/grants/${row.id}`)
  await load()
  await loadScope()
}

onMounted(async () => {
  await load()
  await loadScope()
})
</script>

<template>
  <div class="view">
    <el-card shadow="never">
      <template #header>
        <div class="head">
          <b>业务层 · 智能运维 · 【AI】智能运维授权</b>
          <span class="muted">配置运维助手可见的资源范围（代码仓库 / nacos 配置 / 日志），可导出 X-Data-Scope 交工具 Guardrail 校验</span>
        </div>
      </template>

      <el-tabs v-model="tab">
        <el-tab-pane v-for="t in ['REPO', 'NACOS', 'LOG']" :key="t" :label="TYPE_LABEL[t]" :name="t">
          <div class="toolbar">
            <span class="muted">{{ TYPE_HINT[t] }}</span>
            <el-button type="primary" size="small" @click="openCreate">新建授权</el-button>
          </div>
          <el-table v-loading="loading" :data="visible" border stripe size="small">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="code" label="编码" width="150" />
            <el-table-column prop="name" label="名称" min-width="140" />
            <el-table-column prop="agentCode" label="授权助手" width="150" />
            <el-table-column prop="resourceRef" label="资源引用" min-width="200" show-overflow-tooltip />
            <el-table-column prop="permission" label="权限" width="90" />
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

    <el-card shadow="never">
      <template #header>
        <div class="head">
          <span>运维助手可见范围</span>
          <el-select v-model="scopeAgent" filterable placeholder="选择运维助手" style="width: 220px" @change="loadScope">
            <el-option v-for="a in agents" :key="a.agentCode" :label="`${a.name ?? a.agentCode} (${a.agentCode})`" :value="a.agentCode" />
          </el-select>
          <el-button size="small" @click="loadScope">刷新</el-button>
        </div>
      </template>
      <el-row :gutter="12">
        <el-col :span="8">
          <div class="scope-block"><b>代码仓库</b><ul><li v-for="r in scope.repo" :key="r">{{ r }}</li><li v-if="!scope.repo.length" class="muted">无</li></ul></div>
        </el-col>
        <el-col :span="8">
          <div class="scope-block"><b>nacos 配置</b><ul><li v-for="r in scope.nacos" :key="r">{{ r }}</li><li v-if="!scope.nacos.length" class="muted">无</li></ul></div>
        </el-col>
        <el-col :span="8">
          <div class="scope-block"><b>日志</b><ul><li v-for="r in scope.log" :key="r">{{ r }}</li><li v-if="!scope.log.length" class="muted">无</li></ul></div>
        </el-col>
      </el-row>
    </el-card>

    <el-dialog v-model="dialog" :title="(form.id ? '编辑' : '新建') + (TYPE_LABEL[form.grantType] ?? '授权')" width="560px">
      <el-form label-width="110px">
        <el-form-item label="编码" required><el-input v-model="form.code" :disabled="!!form.id" placeholder="如 grant-order-repo" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="授权助手" required>
          <el-select v-model="form.agentCode" filterable allow-create placeholder="选择或输入智能体编码" style="width: 100%">
            <el-option v-for="a in agents" :key="a.agentCode" :label="`${a.name ?? a.agentCode} (${a.agentCode})`" :value="a.agentCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="授权类型">
          <el-radio-group v-model="form.grantType">
            <el-radio value="REPO">代码仓库</el-radio>
            <el-radio value="NACOS">nacos 配置</el-radio>
            <el-radio value="LOG">日志</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="资源引用">
          <el-input
            v-model="form.resourceRef"
            :placeholder="form.grantType === 'REPO' ? '仓库编码' : form.grantType === 'NACOS' ? 'nacos 服务器 / 配置分类编码' : '日志通道 / 系统'"
          />
        </el-form-item>
        <el-form-item label="权限">
          <el-radio-group v-model="form.permission">
            <el-radio value="READ">只读</el-radio>
            <el-radio value="WRITE">读写</el-radio>
          </el-radio-group>
        </el-form-item>
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
.toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.toolbar .el-button { margin-left: auto; }
.scope-block { border: 1px solid #ebeef5; border-radius: 4px; padding: 10px 12px; }
.scope-block ul { margin: 6px 0 0; padding-left: 18px; font-size: 13px; }
</style>
