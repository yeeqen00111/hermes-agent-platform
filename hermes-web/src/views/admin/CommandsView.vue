<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'

interface CommandBundle {
  id?: number
  bundleCode: string
  name: string
  description?: string
  skillCodes?: string
  status: string
  version?: number
}

interface Skill {
  skillCode: string
  name?: string
  description?: string
  status?: string
}

const loading = ref(false)
const bundles = ref<CommandBundle[]>([])
const skills = ref<Skill[]>([])
const skillOptions = ref<string[]>([])

const dialog = ref(false)
const saving = ref(false)
const form = reactive<CommandBundle>({
  bundleCode: '',
  name: '',
  description: '',
  status: 'ENABLED',
  version: 1,
})

const previewDialog = ref(false)
const previewLoading = ref(false)
const preview = reactive<{ command: string; skills: { code: string; loaded: boolean }[]; missing: string[]; content: string }>({
  command: '',
  skills: [],
  missing: [],
  content: '',
})

function parseCodes(raw?: string): string[] {
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

async function load() {
  loading.value = true
  try {
    const [bundleRes, skillRes] = await Promise.all([
      http.get<CommandBundle[]>('/admin/command-bundles'),
      http.get<Skill[]>('/skills'),
    ])
    bundles.value = bundleRes.data ?? []
    skills.value = skillRes.data ?? []
    skillOptions.value = skills.value.filter((s) => s.status === 'ENABLED').map((s) => s.skillCode)
  } catch (e) {
    ElMessage.error('加载指令失败：' + (e as Error).message)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { id: undefined, bundleCode: '', name: '', description: '', status: 'ENABLED', version: 1 })
  selectedSkills.value = []
  dialog.value = true
}

const selectedSkills = ref<string[]>([])

function openEdit(row: CommandBundle) {
  Object.assign(form, row)
  selectedSkills.value = parseCodes(row.skillCodes)
  dialog.value = true
}

async function save() {
  if (!form.bundleCode || !form.name) {
    ElMessage.warning('指令编码与名称必填')
    return
  }
  saving.value = true
  try {
    await http.post('/admin/command-bundles', { ...form, skillCodes: JSON.stringify(selectedSkills.value) })
    ElMessage.success('已保存')
    dialog.value = false
    await load()
  } catch (e) {
    ElMessage.error('保存失败：' + (e as Error).message)
  } finally {
    saving.value = false
  }
}

async function remove(row: CommandBundle) {
  await ElMessageBox.confirm(`删除指令 /${row.bundleCode}？`, '提示', { type: 'warning' })
  await http.delete(`/admin/command-bundles/${row.id}`)
  await load()
}

async function showPreview(row: CommandBundle) {
  previewLoading.value = true
  previewDialog.value = true
  try {
    const { data } = await http.get(`/admin/command-bundles/${row.id}/preview`)
    preview.command = data.command ?? ''
    preview.skills = data.skills ?? []
    preview.missing = data.missing ?? []
    preview.content = data.content ?? ''
  } catch (e) {
    ElMessage.error('预览失败：' + (e as Error).message)
  } finally {
    previewLoading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="view">
    <el-card shadow="never">
      <template #header>
        <div class="head">
          <b>Agent 层 · 指令（捆绑包）</b>
          <span class="muted">一条指令预载一串技能；对话中以 /指令名 生效（§7.4）</span>
          <el-button type="primary" size="small" @click="openCreate">新建指令</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="bundles" border stripe size="small">
        <el-table-column prop="bundleCode" label="指令" width="160">
          <template #default="{ row }">/ {{ row.bundleCode }}</template>
        </el-table-column>
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column prop="description" label="说明" min-width="200" show-overflow-tooltip />
        <el-table-column label="预载技能" min-width="220">
          <template #default="{ row }">
            <el-tag v-for="c in parseCodes(row.skillCodes)" :key="c" size="small" class="chip">{{ c }}</el-tag>
            <span v-if="!parseCodes(row.skillCodes).length" class="muted">无</span>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="80" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ENABLED' ? 'success' : 'info'">{{ row.status === 'ENABLED' ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="success" size="small" @click="showPreview(row)">预览</el-button>
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialog" :title="form.id ? '编辑指令' : '新建指令'" width="600px">
      <el-form label-width="110px">
        <el-form-item label="指令编码" required><el-input v-model="form.bundleCode" :disabled="!!form.id" placeholder="如 oncall（对话中 /oncall）" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="form.description" /></el-form-item>
        <el-form-item label="预载技能">
          <el-select v-model="selectedSkills" multiple filterable allow-create style="width: 100%" placeholder="选择启用的技能">
            <el-option v-for="c in skillOptions" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="ENABLED">启用</el-radio>
            <el-radio value="DISABLED">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="版本"><el-input-number v-model="form.version" :min="1" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="previewDialog" :title="`拼装预览 · ${preview.command}`" width="720px">
      <div v-loading="previewLoading">
        <p>
          <b>预载技能：</b>
          <el-tag v-for="s in preview.skills" :key="s.code" size="small" :type="s.loaded ? 'success' : 'danger'" class="chip">
            {{ s.code }}{{ s.loaded ? '' : '（无正文）' }}
          </el-tag>
        </p>
        <p v-if="preview.missing.length" class="muted">缺失/未启用：{{ preview.missing.join('、') }}</p>
        <pre class="content">{{ preview.content }}</pre>
      </div>
      <template #footer>
        <el-button @click="previewDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.view { display: flex; flex-direction: column; gap: 12px; }
.head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.head .el-button { margin-left: auto; }
.muted { color: #909399; font-size: 12px; }
.chip { margin-right: 6px; }
.content { background: #f5f7fa; padding: 10px; border-radius: 4px; font-size: 12px; max-height: 380px; overflow: auto; white-space: pre-wrap; }
</style>
