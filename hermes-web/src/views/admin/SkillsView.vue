<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'

interface Skill {
  id?: number
  skillCode: string
  name: string
  description?: string
  tags?: string
  requiresTools?: string
  status?: string
  currentVersion?: number
}

interface SkillVersion {
  id: number
  version: number
  author?: string
  createTime?: string
}

const skills = ref<Skill[]>([])
const loading = ref(false)

const dialogVisible = ref(false)
const saving = ref(false)
const form = reactive<Skill>({ skillCode: '', name: '', status: 'DRAFT' })

const pubVisible = ref(false)
const pubSkill = ref<Skill | null>(null)
const pubForm = reactive<{ content: string; requiresTools: string; author: string }>({
  content: '',
  requiresTools: '',
  author: '',
})

const verVisible = ref(false)
const versions = ref<SkillVersion[]>([])

async function load() {
  loading.value = true
  try {
    const { data } = await http.get<Skill[]>('/skills')
    skills.value = data ?? []
  } catch (e) {
    ElMessage.error('加载技能失败：' + (e as Error).message)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { id: undefined, skillCode: '', name: '', description: '', tags: '', requiresTools: '', status: 'DRAFT' })
  dialogVisible.value = true
}

function openEdit(row: Skill) {
  Object.assign(form, row)
  dialogVisible.value = true
}

async function save() {
  if (!form.skillCode || !form.name) {
    ElMessage.warning('skillCode 与 name 必填')
    return
  }
  saving.value = true
  try {
    await http.post('/skills', { ...form })
    ElMessage.success('已保存')
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error('保存失败：' + (e as Error).message)
  } finally {
    saving.value = false
  }
}

function openPublish(row: Skill) {
  pubSkill.value = row
  pubForm.content = ''
  pubForm.requiresTools = row.requiresTools ?? ''
  pubForm.author = ''
  pubVisible.value = true
}

async function publish() {
  if (!pubSkill.value) return
  if (!pubForm.content.trim()) {
    ElMessage.warning('版本内容不能为空')
    return
  }
  await http.post(`/skills/${pubSkill.value.skillCode}/publish`, { ...pubForm })
  ElMessage.success('已发布新版本')
  pubVisible.value = false
  await load()
}

async function openVersions(row: Skill) {
  verVisible.value = true
  const { data } = await http.get<SkillVersion[]>(`/skills/${row.skillCode}/versions`)
  versions.value = data ?? []
}

onMounted(load)
</script>

<template>
  <div class="view">
    <el-card shadow="never">
      <template #header>
        <div class="head">
          <b>Agent · 技能</b>
          <span class="muted">技能包 + 版本发布；对话里用技能指令装载</span>
          <el-button type="primary" size="small" @click="openCreate">新建技能</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="skills" border stripe size="small">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="skillCode" label="编码" width="160" />
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column prop="tags" label="标签" min-width="140" />
        <el-table-column prop="requiresTools" label="依赖工具" min-width="170" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="currentVersion" label="版本" width="70" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="success" size="small" @click="openPublish(row)">发布</el-button>
            <el-button link type="info" size="small" @click="openVersions(row)">版本</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑技能' : '新建技能'" width="560px">
      <el-form label-width="100px">
        <el-form-item label="编码" required><el-input v-model="form.skillCode" :disabled="!!form.id" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" /></el-form-item>
        <el-form-item label="标签"><el-input v-model="form.tags" placeholder="逗号分隔" /></el-form-item>
        <el-form-item label="依赖工具"><el-input v-model="form.requiresTools" placeholder="逗号分隔，如 log.search,alert.query" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="DRAFT" value="DRAFT" />
            <el-option label="ENABLED" value="ENABLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="pubVisible" :title="`发布技能版本 · ${pubSkill?.name ?? ''}`" width="560px">
      <el-form label-width="100px">
        <el-form-item label="正文" required><el-input v-model="pubForm.content" type="textarea" :rows="6" /></el-form-item>
        <el-form-item label="依赖工具"><el-input v-model="pubForm.requiresTools" /></el-form-item>
        <el-form-item label="发布人"><el-input v-model="pubForm.author" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pubVisible = false">取消</el-button>
        <el-button type="primary" @click="publish">发布</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="verVisible" title="技能版本历史" width="460px">
      <el-table :data="versions" border size="small">
        <el-table-column prop="version" label="版本" width="80" />
        <el-table-column prop="author" label="发布人" width="120" />
        <el-table-column prop="createTime" label="时间" min-width="170" />
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
</style>
