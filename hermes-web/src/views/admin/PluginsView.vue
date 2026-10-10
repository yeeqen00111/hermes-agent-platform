<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'

interface AiPlugin {
  id?: number
  pluginCode: string
  name: string
  version?: string
  pluginType?: string
  description?: string
  manifest?: string
  config?: string
  enabled?: number
  status?: string
}

interface BindingRow {
  agentCode: string
  pluginCode: string
  boundEnabled?: number
  pluginName?: string
  pluginEnabled?: number
  pluginStatus?: string
}

const loading = ref(false)
const plugins = ref<AiPlugin[]>([])
const bindings = ref<BindingRow[]>([])
const agentOptions = ref<{ agentCode: string; name: string }[]>([])

const dialog = ref(false)
const saving = ref(false)
const form = reactive<AiPlugin>({
  pluginCode: '',
  name: '',
  version: '1.0.0',
  pluginType: 'CAPABILITY',
  description: '',
  manifest: '{"tools":[],"skills":[],"commands":[],"prompt":""}',
  config: '',
  enabled: 1,
  status: 'ACTIVE',
})

const verifyDialog = ref(false)
const verify = reactive<{ ok: boolean; issues: string[]; plugins: { pluginCode: string; name: string; active: boolean; missing: string[] }[]; bindingCount: number }>({
  ok: true,
  issues: [],
  plugins: [],
  bindingCount: 0,
})

const MANIFEST_TIP = 'JSON：{"tools":["log.search"],"skills":[],"commands":[],"prompt":"提示词"} —— 插件声明的工具必须已在工具注册表注册'

function parseManifest(raw?: string): { tools: string[]; skills: string[]; commands: string[]; prompt: string } | null {
  if (!raw) return null
  try {
    const p = JSON.parse(raw)
    return {
      tools: Array.isArray(p.tools) ? p.tools : [],
      skills: Array.isArray(p.skills) ? p.skills : [],
      commands: Array.isArray(p.commands) ? p.commands : [],
      prompt: typeof p.prompt === 'string' ? p.prompt : '',
    }
  } catch {
    return null
  }
}

function manifestOf(row: AiPlugin) {
  return parseManifest(row.manifest) ?? { tools: [], skills: [], commands: [], prompt: '' }
}

function manifestValid(raw?: string) {
  return !!parseManifest(raw)
}

async function load() {
  loading.value = true
  try {
    const [p, a] = await Promise.all([
      http.get<AiPlugin[]>('/admin/plugins'),
      http.get<{ agentCode: string; name: string }[]>('/agent-profiles'),
    ])
    plugins.value = p.data ?? []
    agentOptions.value = (a.data ?? []).map((x) => ({ agentCode: x.agentCode, name: x.name }))
  } catch (e) {
    ElMessage.error('加载插件失败：' + (e as Error).message)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, {
    id: undefined,
    pluginCode: '',
    name: '',
    version: '1.0.0',
    pluginType: 'CAPABILITY',
    description: '',
    manifest: '{"tools":[],"skills":[],"commands":[],"prompt":""}',
    config: '',
    enabled: 1,
    status: 'ACTIVE',
  })
  dialog.value = true
}

function openEdit(row: AiPlugin) {
  Object.assign(form, row)
  dialog.value = true
}

async function save() {
  if (!form.pluginCode || !form.name) {
    ElMessage.warning('插件编码与名称必填')
    return
  }
  if (!manifestValid(form.manifest)) {
    ElMessage.warning('能力清单（manifest）不是合法 JSON')
    return
  }
  saving.value = true
  try {
    await http.post('/admin/plugins', { ...form })
    ElMessage.success('已保存并重新装载')
    dialog.value = false
    await load()
  } catch (e) {
    ElMessage.error('保存失败：' + (e as Error).message)
  } finally {
    saving.value = false
  }
}

async function toggleEnabled(row: AiPlugin) {
  const next = row.enabled === 1 ? 0 : 1
  try {
    await http.post(`/admin/plugins/${row.id}/enabled?enabled=${next}`)
    ElMessage.success(next ? '已启用并装载' : '已停用并卸载')
    await load()
  } catch (e) {
    ElMessage.error('操作失败：' + (e as Error).message)
  }
}

async function remove(row: AiPlugin) {
  await ElMessageBox.confirm(`删除插件 ${row.pluginCode}？其所有绑定会一并清除。`, '提示', { type: 'warning' })
  await http.delete(`/admin/plugins/${row.id}`)
  ElMessage.success('已删除')
  await load()
}

async function bind(row: AiPlugin, agentCode?: string) {
  let target = agentCode
  if (!target) {
    const { value } = await ElMessageBox.prompt('输入要绑定的智能体编码', '绑定到智能体', {
      confirmButtonText: '绑定',
      cancelButtonText: '取消',
      inputPattern: /\S+/,
      inputErrorMessage: '请输入智能体编码',
      inputValue: '',
    }).catch(() => ({ value: '' as string }))
    if (!value) return
    target = value
  }
  try {
    const { data } = await http.post(`/admin/plugins/bind?agentCode=${encodeURIComponent(target)}&pluginCode=${encodeURIComponent(row.pluginCode)}`)
    if (data?.active) {
      ElMessage.success(`已绑定到 ${target}，能力随该智能体生效`)
    } else {
      ElMessage.warning(`已绑定到 ${target}，但插件未启用，绑定暂不生效`)
    }
    await load()
  } catch (e) {
    ElMessage.error('绑定失败：' + (e as Error).message)
  }
}

async function unbind(b: BindingRow) {
  await http.post(`/admin/plugins/unbind?agentCode=${encodeURIComponent(b.agentCode)}&pluginCode=${encodeURIComponent(b.pluginCode)}`)
  ElMessage.success('已解除绑定')
  await load()
}

const selectedAgent = ref('')

async function loadBindings() {
  if (!selectedAgent.value) {
    bindings.value = []
    return
  }
  const { data } = await http.get<BindingRow[]>('/admin/plugins/by-agent', { params: { agentCode: selectedAgent.value } })
  bindings.value = data ?? []
}

async function runVerify() {
  const { data } = await http.get('/admin/plugins/verify')
  verify.ok = !!data?.ok
  verify.issues = data?.issues ?? []
  verify.plugins = data?.plugins ?? []
  verify.bindingCount = data?.bindingCount ?? 0
  verifyDialog.value = true
  if (verify.ok) ElMessage.success('插件装载核验通过')
  else ElMessage.warning(`发现 ${verify.issues.length} 个问题`)
}

function effective(b: BindingRow) {
  return b.pluginStatus === 'ACTIVE' && b.pluginEnabled === 1 && b.boundEnabled === 1
}

onMounted(load)
</script>

<template>
  <div class="view">
    <el-card shadow="never">
      <template #header>
        <div class="head">
          <b>Agent 层 · 插件</b>
          <span class="muted">进程内注册的能力包：一组工具 + 技能 + 指令 + 提示词，绑定到智能体后随身份包生效</span>
          <el-button size="small" @click="runVerify">装载核验</el-button>
          <el-button type="primary" size="small" @click="openCreate">注册插件</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="plugins" border stripe size="small">
        <el-table-column prop="pluginCode" label="插件编码" width="150" />
        <el-table-column prop="name" label="名称" min-width="130" />
        <el-table-column prop="version" label="版本" width="80" />
        <el-table-column prop="pluginType" label="类型" width="100" />
        <el-table-column label="能力清单" min-width="240">
          <template #default="{ row }">
            <el-tag v-for="t in manifestOf(row).tools" :key="'t' + t" size="small" class="chip">工具 {{ t }}</el-tag>
            <el-tag v-for="s in manifestOf(row).skills" :key="'s' + s" size="small" type="warning" class="chip">技能 {{ s }}</el-tag>
            <el-tag v-for="c in manifestOf(row).commands" :key="'c' + c" size="small" type="info" class="chip">指令 {{ c }}</el-tag>
            <el-tag v-if="manifestOf(row).prompt" size="small" type="success" class="chip">提示词</el-tag>
            <span v-if="!row.manifest" class="muted">未声明</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.enabled === 1 && row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.enabled === 1 && row.status === 'ACTIVE' ? '已装载' : '已停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link :type="row.enabled === 1 ? 'warning' : 'success'" size="small" @click="toggleEnabled(row)">
              {{ row.enabled === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button link type="success" size="small" @click="bind(row)">绑定</el-button>
            <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="head">
          <b>智能体 · 生效中的插件</b>
          <span class="muted">只有「绑定启用 + 插件已装载」才真正随身份包注入</span>
          <el-select v-model="selectedAgent" size="small" style="width: 200px" placeholder="选择智能体查询" filterable @change="loadBindings">
            <el-option v-for="a in agentOptions" :key="a.agentCode" :label="a.name + '（' + a.agentCode + '）'" :value="a.agentCode" />
          </el-select>
        </div>
      </template>
      <el-table :data="bindings" border stripe size="small">
        <el-table-column prop="agentCode" label="智能体" width="160" />
        <el-table-column prop="pluginCode" label="插件" width="160" />
        <el-table-column prop="pluginName" label="名称" min-width="140" />
        <el-table-column label="是否生效" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="effective(row) ? 'success' : 'danger'">{{ effective(row) ? '生效中' : '未生效' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="pluginStatus" label="插件状态" width="110" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="danger" size="small" @click="unbind(row)">解绑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialog" :title="form.id ? '编辑插件' : '注册插件'" width="640px">
      <el-form label-width="100px">
        <el-form-item label="插件编码" required><el-input v-model="form.pluginCode" :disabled="!!form.id" placeholder="如 ops-triage-pack" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="版本"><el-input v-model="form.version" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.pluginType" style="width: 100%">
            <el-option value="CAPABILITY" label="CAPABILITY（能力包）" />
            <el-option value="PROMPT" label="PROMPT（提示词包）" />
            <el-option value="CHANNEL" label="CHANNEL（渠道包）" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明"><el-input v-model="form.description" /></el-form-item>
        <el-form-item label="能力清单">
          <el-input v-model="form.manifest" type="textarea" :rows="6" />
          <div class="tip">{{ MANIFEST_TIP }}</div>
        </el-form-item>
        <el-form-item label="插件配置"><el-input v-model="form.config" type="textarea" :rows="2" placeholder="JSON，可空" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存并装载</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="verifyDialog" title="插件装载核验" width="720px">
      <p>
        <el-tag :type="verify.ok ? 'success' : 'danger'" size="small">{{ verify.ok ? '通过' : '有问题' }}</el-tag>
        <span class="muted">插件 {{ verify.plugins.length }} 个，绑定 {{ verify.bindingCount }} 条</span>
      </p>
      <el-table :data="verify.plugins" border stripe size="small" style="margin-bottom: 10px">
        <el-table-column prop="pluginCode" label="插件" width="160" />
        <el-table-column prop="name" label="名称" min-width="130" />
        <el-table-column label="装载" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.active ? 'success' : 'info'">{{ row.active ? '装载' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="不可装载项" min-width="220">
          <template #default="{ row }">
            <span v-if="!row.missing?.length" class="muted">无</span>
            <span v-else class="bad">{{ row.missing.join('；') }}</span>
          </template>
        </el-table-column>
      </el-table>
      <el-alert v-if="verify.issues.length" type="warning" :closable="false" title="核验问题">
        <ul class="issues">
          <li v-for="(i, idx) in verify.issues" :key="idx">{{ i }}</li>
        </ul>
      </el-alert>
      <template #footer>
        <el-button @click="verifyDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.view { display: flex; flex-direction: column; gap: 12px; }
.head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.head .el-button { margin-left: auto; }
.head .el-select { margin-left: auto; }
.muted { color: #909399; font-size: 12px; }
.chip { margin-right: 6px; }
.tip { color: #909399; font-size: 12px; line-height: 1.6; }
.bad { color: #f56c6c; font-size: 12px; }
.issues { margin: 0; padding-left: 18px; font-size: 13px; line-height: 1.8; }
</style>
