<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'

interface ReviewTask {
  id: number
  taskUuid: string
  ruleId?: number
  repoId?: number
  branch?: string
  startRevision?: string
  endRevision?: string
  status: string
  triggerType?: string
  triggerBy?: string
  lastReportId?: number
  dispatchTime?: string
  finishTime?: string
  errorMessage?: string
}

interface Repo {
  id: number
  repoCode: string
  name: string
}

interface ReviewRule {
  id: number
  name: string
  ruleCode: string
}

interface ReviewIssue {
  id: number
  severity?: string
  category?: string
  title?: string
  filePath?: string
  lineNo?: number
  status?: string
}

interface ReviewReport {
  id: number
  markdown?: string
  modelName?: string
  createTime?: string
}

const STATUS_FLOW = [
  'PENDING',
  'REVIEWING',
  'FIRST_REVIEW_DONE',
  'RE_REVIEWING',
  'RE_REVIEW_DONE',
  'CLOSED',
  'FAILED',
] as const

type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

const statusType: Record<string, TagType> = {
  PENDING: 'info',
  REVIEWING: 'warning',
  FIRST_REVIEW_DONE: 'primary',
  RE_REVIEWING: 'warning',
  RE_REVIEW_DONE: 'primary',
  CLOSED: 'success',
  FAILED: 'danger',
}

const tasks = ref<ReviewTask[]>([])
const repos = ref<Repo[]>([])
const rules = ref<ReviewRule[]>([])
const loading = ref(false)
const statusFilter = ref('')

const createVisible = ref(false)
const creating = ref(false)
const trigger = reactive<{ repoId?: number; branch: string; startRevision: string; ruleId?: number }>({
  branch: 'main',
  startRevision: '',
})

const detailVisible = ref(false)
const detail = ref<{ task: ReviewTask | null; reports: ReviewReport[]; issues: ReviewIssue[] }>({
  task: null,
  reports: [],
  issues: [],
})

function repoLabel(id?: number) {
  const r = repos.value.find((x) => x.id === id)
  return r ? `${r.name}（${r.repoCode}）` : id ?? '-'
}
function ruleLabel(id?: number) {
  const r = rules.value.find((x) => x.id === id)
  return r ? r.name : id ?? '-'
}

async function loadTasks() {
  loading.value = true
  try {
    const { data } = await http.get<ReviewTask[]>('/review/tasks', {
      params: statusFilter.value ? { status: statusFilter.value } : {},
    })
    tasks.value = data ?? []
  } catch (e) {
    ElMessage.error('加载任务失败：' + (e as Error).message)
  } finally {
    loading.value = false
  }
}

async function loadRefs() {
  try {
    const [r, u] = await Promise.all([http.get<Repo[]>('/review/repos'), http.get<ReviewRule[]>('/review/rules')])
    repos.value = r.data ?? []
    rules.value = u.data ?? []
  } catch {
    /* 引用数据失败不阻塞 */
  }
}

function openCreate() {
  trigger.repoId = undefined
  trigger.ruleId = undefined
  trigger.branch = 'main'
  trigger.startRevision = ''
  createVisible.value = true
}

async function submitTrigger() {
  if (!trigger.repoId || !trigger.ruleId) {
    ElMessage.warning('仓库与规则必选')
    return
  }
  creating.value = true
  try {
    await http.post('/review/tasks', { ...trigger })
    ElMessage.success('已发起评审，任务异步执行中')
    createVisible.value = false
    await loadTasks()
  } catch (e) {
    ElMessage.error('发起失败：' + (e as Error).message)
  } finally {
    creating.value = false
  }
}

async function reReview(row: ReviewTask) {
  await ElMessageBox.confirm(`对任务 ${row.taskUuid.slice(0, 8)} 发起复审？`, '复审', { type: 'warning' })
  await http.post(`/review/tasks/${row.taskUuid}/re-review`)
  ElMessage.success('已发起复审')
  await loadTasks()
}

async function closeTask(row: ReviewTask) {
  await ElMessageBox.confirm(`关闭任务 ${row.taskUuid.slice(0, 8)}？`, '闭环', { type: 'warning' })
  await http.post(`/review/tasks/${row.taskUuid}/close`)
  ElMessage.success('已关闭')
  await loadTasks()
}

async function openDetail(row: ReviewTask) {
  detailVisible.value = true
  detail.value = { task: row, reports: [], issues: [] }
  const { data } = await http.get<{ success: boolean; task: ReviewTask; reports: ReviewReport[]; issues: ReviewIssue[] }>(
    `/review/tasks/${row.taskUuid}`,
  )
  if (data?.success) {
    detail.value = { task: data.task, reports: data.reports ?? [], issues: data.issues ?? [] }
  }
}

onMounted(() => {
  loadTasks()
  loadRefs()
})
</script>

<template>
  <div class="view">
    <el-card shadow="never">
      <template #header>
        <div class="head">
          <b>代码评审 · 评审任务</b>
          <el-select v-model="statusFilter" placeholder="全部状态" clearable size="small" style="width: 180px" @change="loadTasks">
            <el-option v-for="s in STATUS_FLOW" :key="s" :label="s" :value="s" />
          </el-select>
          <el-button type="primary" size="small" @click="openCreate">发起评审</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tasks" border stripe size="small">
        <el-table-column label="任务" width="120">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">{{ row.taskUuid.slice(0, 8) }}</el-button>
          </template>
        </el-table-column>
        <el-table-column label="规则" min-width="140">
          <template #default="{ row }">{{ ruleLabel(row.ruleId) }}</template>
        </el-table-column>
        <el-table-column label="仓库" min-width="160">
          <template #default="{ row }">{{ repoLabel(row.repoId) }}</template>
        </el-table-column>
        <el-table-column prop="branch" label="分支" width="110" />
        <el-table-column label="状态" width="150">
          <template #default="{ row }">
            <el-tag :type="statusType[row.status] ?? 'info'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="triggerType" label="触发" width="90" />
        <el-table-column prop="dispatchTime" label="派发时间" width="170" />
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <el-button
              v-if="row.status === 'FIRST_REVIEW_DONE'"
              link
              type="warning"
              size="small"
              @click="reReview(row)"
            >
              复审
            </el-button>
            <el-button
              v-if="row.status === 'RE_REVIEW_DONE' || row.status === 'FAILED'"
              link
              type="success"
              size="small"
              @click="closeTask(row)"
            >
              关闭
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="createVisible" title="发起评审" width="520px">
      <el-form label-width="96px">
        <el-form-item label="评审规则" required>
          <el-select v-model="trigger.ruleId" style="width: 100%" placeholder="选择规则（决定参与人与权限）">
            <el-option v-for="r in rules" :key="r.id" :label="`${r.name}（${r.ruleCode}）`" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="仓库" required>
          <el-select v-model="trigger.repoId" style="width: 100%" placeholder="选择仓库">
            <el-option v-for="r in repos" :key="r.id" :label="`${r.name}（${r.repoCode}）`" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="分支">
          <el-input v-model="trigger.branch" />
        </el-form-item>
        <el-form-item label="起始版本">
          <el-input v-model="trigger.startRevision" placeholder="留空则由平台按上次同步版本推断" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitTrigger">发起</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" :title="`评审详情 · ${detail.task?.taskUuid?.slice(0, 8) ?? ''}`" width="860px">
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="状态">
          <el-tag :type="detail.task ? statusType[detail.task.status] ?? 'info' : 'info'" size="small">
            {{ detail.task?.status }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="触发">{{ detail.task?.triggerType }} / {{ detail.task?.triggerBy }}</el-descriptions-item>
        <el-descriptions-item label="分支">{{ detail.task?.branch }}</el-descriptions-item>
        <el-descriptions-item label="起始版本">{{ detail.task?.startRevision || '-' }}</el-descriptions-item>
        <el-descriptions-item label="结束版本">{{ detail.task?.endRevision || '-' }}</el-descriptions-item>
        <el-descriptions-item label="完成时间">{{ detail.task?.finishTime || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-alert
        v-if="detail.task?.errorMessage"
        class="err"
        type="error"
        :closable="false"
        :title="`失败原因：${detail.task.errorMessage}`"
      />

      <h4>评审报告（{{ detail.reports.length }}）</h4>
      <el-empty v-if="detail.reports.length === 0" description="暂无报告" :image-size="60" />
      <el-card v-for="rep in detail.reports" :key="rep.id" class="report" shadow="never">
        <div class="report-head">
          <b>报告 #{{ rep.id }}</b>
          <span class="muted">{{ rep.modelName }} · {{ rep.createTime }}</span>
        </div>
        <pre class="markdown">{{ rep.markdown }}</pre>
      </el-card>

      <h4>评审问题（{{ detail.issues.length }}）</h4>
      <el-table :data="detail.issues" border size="small">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="severity" label="级别" width="90" />
        <el-table-column prop="category" label="分类" width="110" />
        <el-table-column prop="title" label="标题" min-width="200" />
        <el-table-column prop="filePath" label="文件" min-width="180" />
        <el-table-column prop="lineNo" label="行" width="70" />
        <el-table-column prop="status" label="状态" width="100" />
      </el-table>
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
.err {
  margin: 10px 0;
}
.report {
  margin-bottom: 8px;
}
.report-head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
}
.markdown {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  max-height: 320px;
  overflow: auto;
  background: #fafafa;
  padding: 8px;
  border-radius: 4px;
}
h4 {
  margin: 12px 0 6px;
}
</style>
