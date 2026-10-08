<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import { getActorUserId, setActorUserId } from '@/api/actor'

interface ReviewIssue {
  id: number
  taskUuid?: string
  reportId?: number
  severity?: string
  category?: string
  title?: string
  filePath?: string
  lineNo?: number
  status?: string
  recheckNote?: string
  createTime?: string
}

const ISSUE_STATUS = ['OPEN', 'FIXED', 'WONT_FIX', 'CLOSED'] as const
type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

const statusType: Record<string, TagType> = {
  OPEN: 'danger',
  FIXED: 'success',
  WONT_FIX: 'info',
  CLOSED: 'primary',
}

const userId = ref(getActorUserId())
const statusFilter = ref('')
const issues = ref<ReviewIssue[]>([])
const loading = ref(false)

const dialogVisible = ref(false)
const current = ref<ReviewIssue | null>(null)
const form = reactive<{ status: string; note: string }>({ status: 'FIXED', note: '' })

async function loadIssues() {
  loading.value = true
  try {
    const { data } = await http.get<ReviewIssue[]>('/review/issues', {
      params: { userId: userId.value, ...(statusFilter.value ? { status: statusFilter.value } : {}) },
    })
    issues.value = data ?? []
  } catch (e) {
    ElMessage.error('加载问题记录失败：' + (e as Error).message)
  } finally {
    loading.value = false
  }
}

function applyUser() {
  setActorUserId(userId.value)
  loadIssues()
}

function openFlow(row: ReviewIssue) {
  current.value = row
  form.status = row.status === 'OPEN' ? 'FIXED' : row.status ?? 'FIXED'
  form.note = row.recheckNote ?? ''
  dialogVisible.value = true
}

async function submitFlow() {
  if (!current.value) return
  try {
    const { data } = await http.post<{ success: boolean; message?: string }>(
      `/review/issues/${current.value.id}/status`,
      { status: form.status, note: form.note },
    )
    if (data?.success) {
      ElMessage.success('已流转')
      dialogVisible.value = false
      await loadIssues()
    } else {
      ElMessage.warning(data?.message ?? '流转未生效')
    }
  } catch (e) {
    ElMessage.error('流转失败：' + (e as Error).message)
  }
}

onMounted(loadIssues)
</script>

<template>
  <div class="view">
    <el-card shadow="never">
      <template #header>
        <div class="head">
          <b>代码评审 · 评审记录（我的问题记录 / 问题闭环）</b>
          <el-input v-model.number="userId" size="small" style="width: 120px" @keyup.enter="applyUser">
            <template #prefix>UID</template>
          </el-input>
          <el-button size="small" @click="applyUser">按我筛选</el-button>
          <el-select v-model="statusFilter" placeholder="全部状态" clearable size="small" style="width: 140px" @change="loadIssues">
            <el-option v-for="s in ISSUE_STATUS" :key="s" :label="s" :value="s" />
          </el-select>
        </div>
      </template>

      <el-table v-loading="loading" :data="issues" border stripe size="small">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="任务" width="110">
          <template #default="{ row }">{{ row.taskUuid?.slice(0, 8) }}</template>
        </el-table-column>
        <el-table-column prop="severity" label="级别" width="90" />
        <el-table-column prop="category" label="分类" width="110" />
        <el-table-column prop="title" label="标题" min-width="200" />
        <el-table-column prop="filePath" label="文件" min-width="180" />
        <el-table-column prop="lineNo" label="行" width="70" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType[row.status] ?? 'info'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="recheckNote" label="复核说明" min-width="140" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openFlow(row)">流转</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" title="问题闭环流转" width="480px">
      <el-form label-width="90px">
        <el-form-item label="问题">
          <span>{{ current?.title }}</span>
        </el-form-item>
        <el-form-item label="目标状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option v-for="s in ISSUE_STATUS" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="复核说明">
          <el-input v-model="form.note" type="textarea" :rows="3" placeholder="如：已修复 / 误报忽略 / 复核通过" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitFlow">提交</el-button>
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
  flex-wrap: wrap;
}
</style>
