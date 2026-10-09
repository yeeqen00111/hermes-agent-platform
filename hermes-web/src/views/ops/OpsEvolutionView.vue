<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'

interface Experience {
  id?: number
  expCode?: string
  agentCode?: string
  problem: string
  cause?: string
  solution?: string
  tags?: string
  systemName?: string
  sourceType?: string
  hits?: number
  successCount?: number
  failCount?: number
  confidence?: number
  status?: string
  lastUsedTime?: string
}

const loading = ref(false)
const items = ref<Experience[]>([])
const stats = ref<Record<string, number>>({})
const filter = reactive({ agentCode: 'assistant', keyword: '', status: '' })

const dialog = ref(false)
const saving = ref(false)
const form = reactive<Experience>({ agentCode: 'assistant', problem: '', cause: '', solution: '', tags: '', systemName: '', sourceType: 'MANUAL' })

const retrieveQuery = ref('')
const retrieveResult = ref<{ problem: string; solution?: string; confidence?: number; score?: number }[]>([])

async function load() {
  loading.value = true
  try {
    const [listRes, statsRes] = await Promise.all([
      http.get<Experience[]>('/ops/evolution/experiences', {
        params: { agentCode: filter.agentCode || undefined, keyword: filter.keyword || undefined, status: filter.status || undefined },
      }),
      http.get('/ops/evolution/stats'),
    ])
    items.value = listRes.data ?? []
    stats.value = statsRes.data ?? {}
  } catch (e) {
    ElMessage.error('加载经验库失败：' + (e as Error).message)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { agentCode: filter.agentCode || 'assistant', problem: '', cause: '', solution: '', tags: '', systemName: '', sourceType: 'MANUAL' })
  dialog.value = true
}

async function save() {
  if (!form.problem) {
    ElMessage.warning('问题描述必填')
    return
  }
  saving.value = true
  try {
    await http.post('/ops/evolution/experiences', { ...form })
    ElMessage.success('已沉淀经验')
    dialog.value = false
    await load()
  } catch (e) {
    ElMessage.error('保存失败：' + (e as Error).message)
  } finally {
    saving.value = false
  }
}

async function feedback(row: Experience, useful: boolean) {
  await http.post(`/ops/evolution/experiences/${row.id}/feedback`, null, { params: { useful } })
  ElMessage.success(useful ? '已记为有效' : '已记为无效')
  await load()
}

async function consolidate() {
  const { data } = await http.post('/ops/evolution/evolve')
  ElMessage.success(`巩固完成：扫描 ${data.scanned}，衰减 ${data.decayed}，淘汰 ${data.deprecated}`)
  await load()
}

async function runRetrieve() {
  if (!retrieveQuery.value.trim()) {
    retrieveResult.value = []
    return
  }
  const { data } = await http.get('/ops/evolution/retrieve', {
    params: { q: retrieveQuery.value, agentCode: filter.agentCode || undefined, topK: 5 },
  })
  retrieveResult.value = data ?? []
}

onMounted(load)
</script>

<template>
  <div class="view">
    <el-card shadow="never">
      <template #header>
        <div class="head">
          <b>Agent 层 · 运维智能体 · 自我进化</b>
          <span class="muted">记录「问题 → 方案」经验 → 复用 → 反馈强化/衰减 → 定时巩固淘汰（闭环）</span>
          <el-button size="small" type="warning" @click="consolidate">立即巩固（自进化）</el-button>
          <el-button size="small" type="primary" @click="openCreate">沉淀经验</el-button>
        </div>
      </template>

      <el-row :gutter="12" class="stats">
        <el-col :span="4"><div class="stat"><div class="num">{{ stats.total ?? 0 }}</div><div class="lbl">经验总数</div></div></el-col>
        <el-col :span="4"><div class="stat"><div class="num">{{ stats.active ?? 0 }}</div><div class="lbl">在用</div></div></el-col>
        <el-col :span="4"><div class="stat"><div class="num">{{ stats.deprecated ?? 0 }}</div><div class="lbl">已淘汰</div></div></el-col>
        <el-col :span="4"><div class="stat"><div class="num">{{ stats.avgConfidence ?? 0 }}</div><div class="lbl">平均置信度</div></div></el-col>
        <el-col :span="4"><div class="stat"><div class="num">{{ stats.totalHits ?? 0 }}</div><div class="lbl">累计复用</div></div></el-col>
      </el-row>

      <div class="toolbar">
        <el-input v-model="filter.agentCode" placeholder="智能体编码" style="width: 160px" />
        <el-input v-model="filter.keyword" placeholder="关键字" clearable style="width: 200px" @keyup.enter="load" />
        <el-select v-model="filter.status" placeholder="全部状态" clearable style="width: 130px">
          <el-option label="在用" value="ACTIVE" />
          <el-option label="已淘汰" value="DEPRECATED" />
        </el-select>
        <el-button type="primary" size="small" @click="load">查询</el-button>
      </div>

      <el-table v-loading="loading" :data="items" border stripe size="small">
        <el-table-column prop="problem" label="问题/症状" min-width="180" show-overflow-tooltip />
        <el-table-column prop="cause" label="原因" min-width="140" show-overflow-tooltip />
        <el-table-column prop="solution" label="处置方案" min-width="200" show-overflow-tooltip />
        <el-table-column prop="tags" label="标签" width="140" show-overflow-tooltip />
        <el-table-column label="置信度" width="110">
          <template #default="{ row }">
            <span :class="{ low: (row.confidence ?? 0) < 0.3 }">{{ row.confidence }}</span>
          </template>
        </el-table-column>
        <el-table-column label="复用/有效/无效" width="140">
          <template #default="{ row }">{{ row.hits ?? 0 }} / {{ row.successCount ?? 0 }} / {{ row.failCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column prop="lastUsedTime" label="最近复用" min-width="170" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status === 'ACTIVE' ? '在用' : '已淘汰' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="反馈" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="success" size="small" @click="feedback(row, true)">有效</el-button>
            <el-button link type="danger" size="small" @click="feedback(row, false)">无效</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never">
      <template #header>经验复用检索（模拟运维助手按问题检索既有经验）</template>
      <div class="toolbar">
        <el-input v-model="retrieveQuery" placeholder="如：订单接口 502 报错" style="width: 360px" @keyup.enter="runRetrieve" />
        <el-button type="primary" size="small" @click="runRetrieve">检索</el-button>
      </div>
      <el-table :data="retrieveResult" border stripe size="small">
        <el-table-column prop="problem" label="命中问题" min-width="180" />
        <el-table-column prop="solution" label="建议方案" min-width="240" show-overflow-tooltip />
        <el-table-column prop="confidence" label="置信度" width="100" />
        <el-table-column prop="score" label="相关度" width="100" />
      </el-table>
    </el-card>

    <el-dialog v-model="dialog" title="沉淀运维经验" width="600px">
      <el-form label-width="100px">
        <el-form-item label="智能体"><el-input v-model="form.agentCode" /></el-form-item>
        <el-form-item label="问题/症状" required><el-input v-model="form.problem" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="原因"><el-input v-model="form.cause" /></el-form-item>
        <el-form-item label="处置方案"><el-input v-model="form.solution" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="标签"><el-input v-model="form.tags" placeholder="逗号分隔，如 gateway,502" /></el-form-item>
        <el-form-item label="所属系统"><el-input v-model="form.systemName" /></el-form-item>
        <el-form-item label="来源">
          <el-select v-model="form.sourceType" style="width: 160px">
            <el-option v-for="t in ['MANUAL', 'ALERT', 'CHAT', 'KB']" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
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
.head .el-button { margin-left: 0; }
.head .el-button:last-child { margin-left: auto; }
.muted { color: #909399; font-size: 12px; }
.stats { margin-bottom: 12px; }
.stat { border: 1px solid #ebeef5; border-radius: 4px; padding: 10px; text-align: center; }
.stat .num { font-size: 18px; font-weight: 600; color: #303133; }
.stat .lbl { color: #909399; font-size: 12px; margin-top: 4px; }
.toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; flex-wrap: wrap; }
.low { color: #f56c6c; }
</style>
