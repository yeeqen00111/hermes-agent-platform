<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'

interface SysProject {
  id?: number
  name: string
  description?: string
  parentId?: number
  status?: string
}

const projects = ref<SysProject[]>([])
const loading = ref(false)

const dialogVisible = ref(false)
const saving = ref(false)
const form = reactive<SysProject>({ name: '', status: 'ACTIVE' })

async function load() {
  loading.value = true
  try {
    const { data } = await http.get<SysProject[]>('/admin/projects')
    projects.value = data ?? []
  } catch (e) {
    ElMessage.error('加载项目失败：' + (e as Error).message)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { id: undefined, name: '', description: '', parentId: undefined, status: 'ACTIVE' })
  dialogVisible.value = true
}

function openEdit(row: SysProject) {
  Object.assign(form, row)
  dialogVisible.value = true
}

async function save() {
  if (!form.name) {
    ElMessage.warning('name 必填')
    return
  }
  saving.value = true
  try {
    await http.post('/admin/projects', { ...form })
    ElMessage.success('已保存')
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error('保存失败：' + (e as Error).message)
  } finally {
    saving.value = false
  }
}

async function remove(row: SysProject) {
  if (!row.id) return
  await ElMessageBox.confirm(`删除项目 ${row.name}？`, '提示', { type: 'warning' })
  await http.delete(`/admin/projects/${row.id}`)
  await load()
}

onMounted(load)
</script>

<template>
  <div class="view">
    <el-card shadow="never">
      <template #header>
        <div class="head">
          <b>系统层 · 项目管理</b>
          <el-button type="primary" size="small" @click="openCreate">新建项目</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="projects" border stripe size="small">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="description" label="描述" min-width="220" />
        <el-table-column prop="parentId" label="父项目" width="100" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑项目' : '新建项目'" width="520px">
      <el-form label-width="90px">
        <el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="父项目ID"><el-input-number v-model="form.parentId" :min="0" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="ACTIVE" value="ACTIVE" />
            <el-option label="ARCHIVED" value="ARCHIVED" />
          </el-select>
        </el-form-item>
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
</style>
