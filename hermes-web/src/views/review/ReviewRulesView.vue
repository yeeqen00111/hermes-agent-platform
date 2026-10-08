<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'

interface ReviewRule {
  id?: number
  ruleCode: string
  name: string
  triggerType: string
  cronExpr?: string
  repoIds?: string
  branchFilter?: string
  participantIds?: string
  enabled: number
}

interface Repo {
  id: number
  repoCode: string
  name: string
}

interface Participant {
  id?: number
  ruleId?: number
  userId?: number
  role: string
  email?: string
  feishu?: string
}

const TRIGGERS = [
  { value: 'MANUAL', label: '人工发起' },
  { value: 'WEBHOOK', label: 'webhook 触发' },
  { value: 'SCHEDULE', label: '定期扫描' },
]
const ROLES = [
  { value: 'INITIATOR', label: '发起人 INITIATOR' },
  { value: 'RECIPIENT', label: '接收报告 RECIPIENT' },
  { value: 'CLOSER', label: '闭环 CLOSER' },
]

const rules = ref<ReviewRule[]>([])
const repos = ref<Repo[]>([])
const loading = ref(false)

const dialogVisible = ref(false)
const saving = ref(false)
const form = reactive<ReviewRule>({ ruleCode: '', name: '', triggerType: 'MANUAL', enabled: 1 })

const partVisible = ref(false)
const currentRule = ref<ReviewRule | null>(null)
const participants = ref<Participant[]>([])
const partForm = reactive<Participant>({ role: 'INITIATOR', email: '', feishu: '' })

const repoIdList = computed<number[]>({
  get: () =>
    form.repoIds
      ? form.repoIds
          .split(',')
          .map((s) => Number(s.trim()))
          .filter((n) => Number.isFinite(n) && n > 0)
      : [],
  set: (v) => {
    form.repoIds = v.join(',')
  },
})

function triggerLabel(v: string) {
  return TRIGGERS.find((t) => t.value === v)?.label ?? v
}

async function loadRules() {
  loading.value = true
  try {
    const { data } = await http.get<ReviewRule[]>('/review/rules')
    rules.value = data ?? []
  } catch (e) {
    ElMessage.error('加载规则失败：' + (e as Error).message)
  } finally {
    loading.value = false
  }
}

async function loadRepos() {
  try {
    const { data } = await http.get<Repo[]>('/review/repos')
    repos.value = data ?? []
  } catch {
    /* 仓库列表失败不阻塞规则页 */
  }
}

function openCreate() {
  Object.assign(form, {
    id: undefined,
    ruleCode: '',
    name: '',
    triggerType: 'MANUAL',
    cronExpr: '',
    repoIds: '',
    branchFilter: '',
    participantIds: '',
    enabled: 1,
  })
  dialogVisible.value = true
}

function openEdit(row: ReviewRule) {
  Object.assign(form, row)
  dialogVisible.value = true
}

async function save() {
  if (!form.ruleCode || !form.name) {
    ElMessage.warning('ruleCode 与 name 必填')
    return
  }
  saving.value = true
  try {
    await http.post('/review/rules', { ...form })
    ElMessage.success('已保存')
    dialogVisible.value = false
    await loadRules()
  } catch (e) {
    ElMessage.error('保存失败：' + (e as Error).message)
  } finally {
    saving.value = false
  }
}

async function openParticipants(row: ReviewRule) {
  currentRule.value = row
  partVisible.value = true
  Object.assign(partForm, { role: 'INITIATOR', email: '', feishu: '' })
  await loadParticipants()
}

async function loadParticipants() {
  if (!currentRule.value?.id) return
  const { data } = await http.get<Participant[]>(`/review/rules/${currentRule.value.id}/participants`)
  participants.value = data ?? []
}

async function addParticipant() {
  if (!currentRule.value?.id) {
    ElMessage.warning('规则需先保存后再维护参与人')
    return
  }
  try {
    await http.post(`/review/rules/${currentRule.value.id}/participants`, { ...partForm })
    await loadParticipants()
    Object.assign(partForm, { role: partForm.role, email: '', feishu: '' })
  } catch (e) {
    ElMessage.error('添加失败：' + (e as Error).message)
  }
}

async function removeParticipant(p: Participant) {
  if (!p.id) return
  await ElMessageBox.confirm('确认删除该参与人？', '提示', { type: 'warning' })
  await http.delete(`/review/participants/${p.id}`)
  await loadParticipants()
}

onMounted(() => {
  loadRules()
  loadRepos()
})
</script>

<template>
  <div class="view">
    <el-card shadow="never">
      <template #header>
        <div class="head">
          <b>代码评审 · 评审规则配置</b>
          <span class="muted">怎么触发 / 哪些仓库参与 / 哪些人参与（发起·接收报告·闭环）</span>
          <el-button type="primary" size="small" @click="openCreate">新建规则</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="rules" border stripe size="small">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="ruleCode" label="规则编码" width="150" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column label="触发方式" width="120">
          <template #default="{ row }">{{ triggerLabel(row.triggerType) }}</template>
        </el-table-column>
        <el-table-column prop="cronExpr" label="cron" width="130" />
        <el-table-column prop="branchFilter" label="分支过滤" width="140" />
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" size="small" @click="openParticipants(row)">参与人</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑规则' : '新建规则'" width="560px">
      <el-form label-width="96px">
        <el-form-item label="规则编码" required>
          <el-input v-model="form.ruleCode" placeholder="如 default-java-review" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="触发方式">
          <el-select v-model="form.triggerType" style="width: 100%">
            <el-option v-for="t in TRIGGERS" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="cron 表达式">
          <el-input v-model="form.cronExpr" placeholder="定期扫描时填写，如 0 0 2 * * ?" />
        </el-form-item>
        <el-form-item label="参与仓库">
          <el-select v-model="repoIdList" multiple filterable style="width: 100%" placeholder="选择参与评审的仓库">
            <el-option v-for="r in repos" :key="r.id" :label="`${r.name}（${r.repoCode}）`" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="分支过滤">
          <el-input v-model="form.branchFilter" placeholder="如 main,release/*" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="partVisible" :title="`参与人 · ${currentRule?.name ?? ''}`" size="480px">
      <el-form :inline="true" class="part-form">
        <el-form-item label="角色">
          <el-select v-model="partForm.role" style="width: 190px">
            <el-option v-for="r in ROLES" :key="r.value" :label="r.label" :value="r.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="用户ID">
          <el-input v-model.number="partForm.userId" style="width: 110px" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="partForm.email" style="width: 180px" />
        </el-form-item>
        <el-form-item label="飞书">
          <el-input v-model="partForm.feishu" style="width: 180px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="small" @click="addParticipant">添加</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="participants" border size="small">
        <el-table-column prop="role" label="角色" width="120" />
        <el-table-column prop="userId" label="用户ID" width="90" />
        <el-table-column prop="email" label="邮箱" min-width="150" />
        <el-table-column prop="feishu" label="飞书" min-width="120" />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button link type="danger" size="small" @click="removeParticipant(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
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
.part-form {
  margin-bottom: 8px;
  flex-wrap: wrap;
}
</style>
